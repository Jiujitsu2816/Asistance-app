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
    private List<Quadrant> quadrants = new ArrayList<>();
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
            replaceAttendance(rebuild(requests));
        } catch (IOException | RuntimeException exception) {
            lockChannel.close();
            throw exception;
        }
    }

    public synchronized boolean needsSetup() {
        ensureOpen();
        return accounts.keySet().stream().noneMatch(name -> !name.equals("root"));
    }

    public synchronized void createFirstManager(String username, char[] password) throws IOException {
        if (!needsSetup()) throw new IllegalStateException("El responsable ya esta configurado");
        if (username != null && "root".equals(username.trim()))
            throw new IllegalArgumentException("Elige otro nombre de usuario");
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
        if ("root".equals(username)) throw new IllegalArgumentException("Elige otro nombre de usuario");
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
        replaceAttendance(rebuild(requests));
    }

    public record UserInfo(String username, Role role, Employe employee) { }

    public synchronized List<UserInfo> allUsers(Session session) {
        Account viewer = requireRole(session, Role.MANAGER);
        return accounts.values().stream().filter(a -> !a.username.equals("root") || viewer.username.equals("root"))
                .map(a -> new UserInfo(a.username, a.role, a.employee)).toList();
    }

    public synchronized boolean isRoot(Session session) {
        Account user = account(session);
        return user.role == Role.MANAGER && user.username.equals("root");
    }

    private Account requireRoot(Session session) {
        if (!isRoot(session)) throw new SecurityException("No tienes permiso para esta operacion");
        return account(session);
    }

    public synchronized void resetApplication(Session session) throws IOException {
        Account root = requireRoot(session);
        var remaining = new LinkedHashMap<String, Account>();
        remaining.put(root.username, root);
        save(remaining, List.of(), List.of());
        quadrants.clear();
        accounts.clear();
        accounts.putAll(remaining);
        requests = new ArrayList<>();
        replaceAttendance(rebuild(requests));
        sessions.clear();
        try {
            for (Path backup : backupFiles()) Files.delete(backup);
        } catch (IOException exception) {
            throw new IOException("Reset aplicado, pero no se pudieron borrar todos los backups: "
                    + exception.getMessage(), exception);
        }
    }

    public synchronized Path createBackup(Session session) throws IOException {
        requireRoot(session);
        return createBackup();
    }

    private List<Path> backupFiles() throws IOException {
        String prefix = file.getFileName() + ".backup-";
        try (var entries = Files.list(file.getParent())) {
            return entries.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
        }
    }

    private Path createBackup() throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".backup-" + UUID.randomUUID());
        Files.copy(file, backup);
        // Explicitly keep the newly created copy, even when timestamps are identical.
        List<Path> previous = new ArrayList<>(backupFiles());
        previous.remove(backup);
        Map<Path, java.nio.file.attribute.FileTime> times = new HashMap<>();
        for (Path path : previous) times.put(path, Files.getLastModifiedTime(path));
        previous.sort(Comparator.<Path, java.nio.file.attribute.FileTime>comparing(times::get).reversed()
                .thenComparing(p -> p.getFileName().toString()));
        // Files.copy may retain the source timestamp. Give each backup a distinct
        // creation order, even when the clock is fixed or the source is unchanged.
        Instant created = clock.instant();
        if (!previous.isEmpty() && !created.isAfter(times.get(previous.getFirst()).toInstant()))
            created = times.get(previous.getFirst()).toInstant().plusMillis(1);
        Files.setLastModifiedTime(backup, java.nio.file.attribute.FileTime.from(created));
        for (int i = 1; i < previous.size(); i++) Files.delete(previous.get(i));
        return backup;
    }

    public synchronized void resetPassword(Session session, String username, char[] password) throws IOException {
        requireRole(session, Role.MANAGER);
        Account old = accounts.get(username);
        if ("root".equals(username) && !isRoot(session))
            throw new IllegalArgumentException("Usuario desconocido");
        if (old == null) throw new IllegalArgumentException("Usuario desconocido");
        var updated = new LinkedHashMap<>(accounts);
        updated.put(username, withPassword(old, password));
        save(updated, requests);
        accounts.put(username, updated.get(username));
        sessions.entrySet().removeIf(e -> e.getValue().equals(username) && !e.getKey().equals(session.token()));
    }

    private static Account withPassword(Account old, char[] password) {
        if (password == null || password.length < 8)
            throw new IllegalArgumentException("La clave debe tener al menos 8 caracteres");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new Account(old.username, Base64.getEncoder().encodeToString(salt), hash(password, salt),
                old.role, old.employee);
    }

    // Recovery requires local filesystem access and the exclusive broker lock.
    synchronized Map<String, String> recoverUsers() throws IOException {
        ensureOpen();
        Account root = accounts.get("root");
        if (root != null && root.role != Role.MANAGER)
            throw new IllegalStateException("El nombre root pertenece a un empleado");
        var updated = new LinkedHashMap<>(accounts);
        updated.putIfAbsent("root", new Account("root", "", "", Role.MANAGER, null));
        Map<String, String> credentials = new LinkedHashMap<>();
        for (Account account : List.copyOf(updated.values())) {
            String password = UUID.randomUUID().toString();
            char[] secret = password.toCharArray();
            try { updated.put(account.username, withPassword(account, secret)); }
            finally { Arrays.fill(secret, '\0'); }
            credentials.put(account.username, password);
        }
        if (Files.exists(file)) {
            Path backup = createBackup();
            System.out.println("Copia de seguridad: " + backup);
        }
        save(updated, requests);
        accounts.clear();
        accounts.putAll(updated);
        sessions.clear();
        return credentials;
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
        try (AttendanceService validation = rebuild(requests)) {
            apply(validation, account.employee.id(), place, type, now);
        }
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
        try { save(accounts, updated); }
        catch (IOException | RuntimeException exception) { rebuilt.close(); throw exception; }
        requests = updated;
        replaceAttendance(rebuilt);
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
        attendance.close();
        try { lock.release(); }
        finally { lockChannel.close(); }
    }

    private AttendanceService rebuild(List<AttendanceRequest> source) {
        AttendanceService service = new AttendanceService(clock, false);
        accounts.values().stream().filter(a -> a.employee != null).forEach(a -> service.registerEmployee(a.employee));
        for (AttendanceRequest request : source)
            if (request.status() == AttendanceRequest.Status.APPROVED)
                apply(service, request.employee().id(), request.place(), request.type(), request.requestedAt());
        quadrants.forEach(service::registerQuadrant);
        return service;
    }

    private void replaceAttendance(AttendanceService replacement) {
        if (attendance != null) attendance.close();
        attendance = replacement;
    }

    public synchronized void registerQuadrant(Session session, Quadrant quadrant) throws IOException {
        requireRole(session, Role.MANAGER);
        Objects.requireNonNull(quadrant);
        if (accounts.values().stream().noneMatch(a -> quadrant.employe().equals(a.employee)))
            throw new IllegalArgumentException("Empleado desconocido");
        var updated = new ArrayList<>(quadrants);
        if (updated.contains(quadrant)) return;
        updated.add(quadrant);
        save(accounts, requests, updated);
        quadrants = updated;
        replaceAttendance(rebuild(requests));
    }

    public synchronized List<Quadrant> allQuadrants(Session session) {
        requireRole(session, Role.MANAGER);
        return List.copyOf(quadrants);
    }

    public synchronized List<Missing> myMissing(Session session) {
        return attendance.missingHistory(requireRole(session, Role.EMPLOYEE).employee.id());
    }

    public synchronized List<Missing> allMissing(Session session) {
        requireRole(session, Role.MANAGER);
        return accounts.values().stream().filter(a -> a.employee != null)
                .flatMap(a -> attendance.missingHistory(a.employee.id()).stream()).toList();
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
        save(users, items, quadrants);
    }

    private void save(Map<String, Account> users, List<AttendanceRequest> items, List<Quadrant> schedules) throws IOException {
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
        data.setProperty("quadrants", Integer.toString(schedules.size()));
        for (int q = 0; q < schedules.size(); q++) {
            Quadrant schedule = schedules.get(q);
            String p = "quadrant." + q + ".";
            writeEmployee(data, p, schedule.employe());
            data.setProperty(p + "place", schedule.place().name());
            data.setProperty(p + "placeType", schedule.place().type());
            data.setProperty(p + "x", Integer.toString(schedule.place().x()));
            data.setProperty(p + "y", Integer.toString(schedule.place().y()));
            data.setProperty(p + "zone", schedule.zone().getId());
            data.setProperty(p + "entries", Integer.toString(schedule.labor().size()));
            for (int e = 0; e < schedule.labor().size(); e++) {
                data.setProperty(p + "date." + e, schedule.labor().get(e).toString());
                data.setProperty(p + "time." + e, schedule.timetable().get(e).toString());
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
            for (int i = 0, count = storedCount(data, "users", null); i < count; i++) {
                String p = "user." + i + ".";
                Role role = Role.valueOf(data.getProperty(p + "role"));
                String username = Objects.requireNonNull(data.getProperty(p + "name"));
                Account account = new Account(username, Objects.requireNonNull(data.getProperty(p + "salt")),
                        Objects.requireNonNull(data.getProperty(p + "hash")), role,
                        role == Role.EMPLOYEE ? readEmployee(data, p) : null);
                if (accounts.putIfAbsent(username, account) != null) throw new IllegalArgumentException("Usuario duplicado");
            }
            for (int i = 0, count = storedCount(data, "requests", null); i < count; i++) {
                String p = "request." + i + ".";
                String decidedAt = data.getProperty(p + "decidedAt");
                requests.add(new AttendanceRequest(UUID.fromString(data.getProperty(p + "id")), readEmployee(data, p),
                        new Place(data.getProperty(p + "place"), data.getProperty(p + "placeType"),
                                Integer.parseInt(data.getProperty(p + "x")), Integer.parseInt(data.getProperty(p + "y"))),
                        AttendanceRequest.Type.valueOf(data.getProperty(p + "type")), Instant.parse(data.getProperty(p + "time")),
                        AttendanceRequest.Status.valueOf(data.getProperty(p + "status")), data.getProperty(p + "decidedBy"),
                        decidedAt == null ? null : Instant.parse(decidedAt), data.getProperty(p + "reason")));
            }
            for (int q = 0, count = storedCount(data, "quadrants", "0"); q < count; q++) {
                String p = "quadrant." + q + ".";
                var dates = new ArrayList<java.time.LocalDate>();
                var times = new ArrayList<java.time.LocalTime>();
                for (int e = 0, countEntries = storedCount(data, p + "entries", null); e < countEntries; e++) {
                    dates.add(java.time.LocalDate.parse(data.getProperty(p + "date." + e)));
                    times.add(java.time.LocalTime.parse(data.getProperty(p + "time." + e)));
                }
                quadrants.add(new Quadrant(readEmployee(data, p),
                        new Place(data.getProperty(p + "place"), data.getProperty(p + "placeType"),
                                Integer.parseInt(data.getProperty(p + "x")), Integer.parseInt(data.getProperty(p + "y"))),
                        dates, times, java.time.ZoneId.of(data.getProperty(p + "zone"))));
            }
        } catch (RuntimeException exception) { throw new IOException("El registro guardado no es valido", exception); }
    }

    private static int storedCount(Properties data, String key, String fallback) {
        int count = Integer.parseInt(data.getProperty(key, fallback));
        if (count < 0) throw new IllegalArgumentException("Recuento negativo: " + key);
        return count;
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
