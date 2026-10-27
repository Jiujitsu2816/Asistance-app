package Software.ulpgc;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Single-process broker. All access from the views goes through authenticated sessions. */
public final class AttendanceBroker implements AutoCloseable {
    public enum Role { MANAGER, EMPLOYEE }
    public record Session(UUID token) { }
    private record Account(String username, String salt, String passwordHash,
                           Role role, Employe employee) { }

    private final Path file;
    private final Clock clock;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<UUID, String> sessions = new HashMap<>();
    private List<AttendanceRequest> requests = new ArrayList<>();
    private AttendanceService attendance;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    public AttendanceBroker(Path file, Clock clock) throws IOException {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        this.clock = Objects.requireNonNull(clock);
        Files.createDirectories(this.file.getParent());
        lockChannel = FileChannel.open(this.file.resolveSibling(this.file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock acquired;
            try { acquired = lockChannel.tryLock(); }
            catch (OverlappingFileLockException exception) { acquired = null; }
            if (acquired == null) throw new IOException("El registro ya esta abierto. Usa Otra ventana en la aplicacion.");
            lock = acquired;
            load();
            attendance = rebuild(requests);
        } catch (IOException | RuntimeException exception) {
            lockChannel.close();
            throw exception;
        }
    }

    public synchronized boolean needsSetup() { ensureOpen(); return accounts.isEmpty(); }

    public synchronized void createFirstManager(String username, char[] password) throws IOException {
        if (!needsSetup()) throw new IllegalStateException("El responsable ya esta configurado");
        addAccount(username, password, Role.MANAGER, null);
    }

    public synchronized void registerEmployee(Session session, String username, char[] password,
                                              Employe employee) throws IOException {
        requireRole(session, Role.MANAGER);
        Objects.requireNonNull(employee);
        if (accounts.values().stream().anyMatch(a -> a.employee != null && a.employee.id() == employee.id()))
            throw new IllegalArgumentException("El ID de empleado ya existe");
        addAccount(username, password, Role.EMPLOYEE, employee);
    }

    private void addAccount(String username, char[] password, Role role, Employe employee) throws IOException {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Indica el usuario");
        username = username.trim();
        if (accounts.containsKey(username)) throw new IllegalArgumentException("El usuario ya existe");
        if (password == null || password.length < 8)
            throw new IllegalArgumentException("La clave debe tener al menos 8 caracteres");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Account account = new Account(username, Base64.getEncoder().encodeToString(salt),
                hash(password, salt), role, employee);
        var updated = new LinkedHashMap<>(accounts);
        updated.put(username, account);
        save(updated, requests);
        accounts.put(username, account);
        attendance = rebuild(requests);
    }

    public synchronized Session login(String username, char[] password) {
        ensureOpen();
        Account account = accounts.get(username == null ? "" : username.trim());
        if (account == null || password == null || !MessageDigest.isEqual(
                Base64.getDecoder().decode(account.passwordHash),
                Base64.getDecoder().decode(hash(password, Base64.getDecoder().decode(account.salt)))))
            throw new SecurityException("Usuario o clave incorrectos");
        Session session = new Session(UUID.randomUUID());
        sessions.put(session.token(), account.username);
        return session;
    }

    public synchronized void logout(Session session) {
        if (session != null) sessions.remove(session.token());
    }

    public synchronized Role role(Session session) { return account(session).role; }

    public synchronized String displayName(Session session) {
        Account account = account(session);
        return account.employee == null ? account.username
                : account.employee.name() + " " + account.employee.lastname();
    }

    public synchronized AttendanceRequest requestCheckIn(Session session, Place place) throws IOException {
        return submit(session, Objects.requireNonNull(place), AttendanceRequest.Type.CHECK_IN);
    }

    public synchronized AttendanceRequest requestCheckOut(Session session) throws IOException {
        Account account = requireRole(session, Role.EMPLOYEE);
        Attendance open = attendance.history(account.employee.id()).stream().filter(Attendance::isOpen)
                .findFirst().orElseThrow(() -> new IllegalStateException("No tienes una entrada aprobada abierta"));
        return submit(session, open.place(), AttendanceRequest.Type.CHECK_OUT);
    }

    private AttendanceRequest submit(Session session, Place place, AttendanceRequest.Type type) throws IOException {
        Account account = requireRole(session, Role.EMPLOYEE);
        if (requests.stream().anyMatch(r -> r.employee().id() == account.employee.id()
                && r.status() == AttendanceRequest.Status.PENDING))
            throw new IllegalStateException("Ya tienes una solicitud pendiente de revision");
        Instant now = clock.instant();
        // Validate without changing the approved history.
        apply(rebuild(requests), account.employee.id(), place, type, now);
        AttendanceRequest request = new AttendanceRequest(UUID.randomUUID(), account.employee, place,
                type, now, AttendanceRequest.Status.PENDING, null, null, "");
        var updated = new ArrayList<>(requests);
        updated.add(request);
        save(accounts, updated);
        requests = updated;
        return request;
    }

    public synchronized List<AttendanceRequest> myRequests(Session session) {
        int id = requireRole(session, Role.EMPLOYEE).employee.id();
        return requests.stream().filter(r -> r.employee().id() == id).toList();
    }

    public synchronized List<Attendance> myHistory(Session session) {
        return attendance.history(requireRole(session, Role.EMPLOYEE).employee.id());
    }

    public synchronized Duration myWorkedHours(Session session) {
        return myHistory(session).stream().filter(a -> !a.isOpen()).map(Attendance::workedDuration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    public synchronized List<AttendanceRequest> allRequests(Session session) {
        requireRole(session, Role.MANAGER);
        return List.copyOf(requests);
    }

    public synchronized List<AttendanceRequest> pendingRequests(Session session) {
        return allRequests(session).stream().filter(r -> r.status() == AttendanceRequest.Status.PENDING).toList();
    }

    public synchronized void decide(Session session, UUID requestId, boolean approve, String reason) throws IOException {
        Account manager = requireRole(session, Role.MANAGER);
        var updated = new ArrayList<>(requests);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) if (updated.get(i).id().equals(requestId)) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException("Solicitud desconocida");
        AttendanceRequest old = updated.get(index);
        if (old.status() != AttendanceRequest.Status.PENDING)
            throw new IllegalStateException("La solicitud ya tiene una decision");
        updated.set(index, new AttendanceRequest(old.id(), old.employee(), old.place(), old.type(),
                old.requestedAt(), approve ? AttendanceRequest.Status.APPROVED : AttendanceRequest.Status.DENIED,
                manager.username, clock.instant(), reason == null ? "" : reason.trim()));
        AttendanceService rebuilt = rebuild(updated);
        save(accounts, updated);
        requests = updated;
        attendance = rebuilt;
    }

    private Account account(Session session) {
        ensureOpen();
        String username = session == null ? null : sessions.get(session.token());
        if (username == null) throw new SecurityException("Inicia sesion para continuar");
        return accounts.get(username);
    }

    private Account requireRole(Session session, Role role) {
        Account account = account(session);
        if (account.role != role) throw new SecurityException("No tienes permiso para esta operacion");
        return account;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("El registro esta cerrado");
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        sessions.clear();
        try { lock.release(); }
        finally { lockChannel.close(); }
    }

    private AttendanceService rebuild(List<AttendanceRequest> source) {
        AttendanceService service = new AttendanceService(clock);
        accounts.values().stream().filter(a -> a.employee != null).forEach(a -> service.registerEmployee(a.employee));
        for (AttendanceRequest request : source)
            if (request.status() == AttendanceRequest.Status.APPROVED)
                apply(service, request.employee().id(), request.place(), request.type(), request.requestedAt());
        return service;
    }

    private static void apply(AttendanceService service, int id, Place place, AttendanceRequest.Type type, Instant time) {
        if (type == AttendanceRequest.Type.CHECK_IN) service.checkInAt(id, place, time);
        else service.checkOutAt(id, time);
    }

    private static String hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo proteger la clave", exception);
        } finally { spec.clearPassword(); }
    }

    private void save(Map<String, Account> users, List<AttendanceRequest> items) throws IOException {
        Properties data = new Properties();
        data.setProperty("version", "1");
        data.setProperty("users", Integer.toString(users.size()));
        int i = 0;
        for (Account account : users.values()) {
            String prefix = "user." + i++ + ".";
            data.setProperty(prefix + "name", account.username);
            data.setProperty(prefix + "salt", account.salt);
            data.setProperty(prefix + "hash", account.passwordHash);
            data.setProperty(prefix + "role", account.role.name());
            if (account.employee != null) writeEmployee(data, prefix, account.employee);
        }
        data.setProperty("requests", Integer.toString(items.size()));
        for (i = 0; i < items.size(); i++) {
            AttendanceRequest r = items.get(i);
            String prefix = "request." + i + ".";
            data.setProperty(prefix + "id", r.id().toString());
            writeEmployee(data, prefix, r.employee());
            data.setProperty(prefix + "place", r.place().name());
            data.setProperty(prefix + "placeType", r.place().type());
            data.setProperty(prefix + "x", Integer.toString(r.place().x()));
            data.setProperty(prefix + "y", Integer.toString(r.place().y()));
            data.setProperty(prefix + "type", r.type().name());
            data.setProperty(prefix + "time", r.requestedAt().toString());
            data.setProperty(prefix + "status", r.status().name());
            data.setProperty(prefix + "reason", r.reason());
            if (r.decidedAt() != null) {
                data.setProperty(prefix + "decidedAt", r.decidedAt().toString());
                data.setProperty(prefix + "decidedBy", r.decidedBy());
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "attendance-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { data.store(output, "Attendance broker"); }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private void load() throws IOException {
        if (!Files.exists(file)) return;
        Properties data = new Properties();
        try (InputStream input = Files.newInputStream(file)) { data.load(input); }
        try {
            if (!"1".equals(data.getProperty("version"))) throw new IllegalArgumentException("Version desconocida");
            for (int i = 0; i < Integer.parseInt(data.getProperty("users")); i++) {
                String p = "user." + i + ".";
                Role role = Role.valueOf(data.getProperty(p + "role"));
                String username = Objects.requireNonNull(data.getProperty(p + "name"));
                Account account = new Account(username, Objects.requireNonNull(data.getProperty(p + "salt")),
                        Objects.requireNonNull(data.getProperty(p + "hash")), role,
                        role == Role.EMPLOYEE ? readEmployee(data, p) : null);
                if (accounts.putIfAbsent(username, account) != null) throw new IllegalArgumentException("Usuario duplicado");
            }
            for (int i = 0; i < Integer.parseInt(data.getProperty("requests")); i++) {
                String p = "request." + i + ".";
                String decidedAt = data.getProperty(p + "decidedAt");
                requests.add(new AttendanceRequest(UUID.fromString(data.getProperty(p + "id")), readEmployee(data, p),
                        new Place(data.getProperty(p + "place"), data.getProperty(p + "placeType"),
                                Integer.parseInt(data.getProperty(p + "x")), Integer.parseInt(data.getProperty(p + "y"))),
                        AttendanceRequest.Type.valueOf(data.getProperty(p + "type")), Instant.parse(data.getProperty(p + "time")),
                        AttendanceRequest.Status.valueOf(data.getProperty(p + "status")), data.getProperty(p + "decidedBy"),
                        decidedAt == null ? null : Instant.parse(decidedAt), data.getProperty(p + "reason")));
            }
        } catch (RuntimeException exception) { throw new IOException("El registro guardado no es valido", exception); }
    }

    private static void writeEmployee(Properties data, String prefix, Employe employee) {
        data.setProperty(prefix + "employeeId", Integer.toString(employee.id()));
        data.setProperty(prefix + "firstName", employee.name());
        data.setProperty(prefix + "lastName", employee.lastname());
    }

    private static Employe readEmployee(Properties data, String prefix) {
        return new Employe(data.getProperty(prefix + "firstName"), data.getProperty(prefix + "lastName"),
                Integer.parseInt(data.getProperty(prefix + "employeeId")));
    }
}
