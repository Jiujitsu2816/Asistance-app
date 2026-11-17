package Software.ulpgc;

import java.io.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Single-process broker. All access from the views goes through authenticated sessions. */
public final class AttendanceBroker implements AutoCloseable {
    public enum Role { MANAGER, EMPLOYEE }
    public record Session(UUID token) { }

    private final AttendanceStore store;
    private final PasswordHasher passwords;
    private final AbsencePolicy absencePolicy;
    private final Clock clock;
    private final Map<String, UserAccount> accounts = new LinkedHashMap<>();
    private final Map<UUID, String> sessions = new HashMap<>();
    private List<AttendanceRequest> requests = new ArrayList<>();
    private AttendanceService attendance;
    private List<Quadrant> quadrants = new ArrayList<>();
    private boolean closed;

    public AttendanceBroker(Path file, Clock clock) throws IOException {
        this(new PropertiesAttendanceStore(file, clock), clock, new Pbkdf2PasswordHasher());
    }

    /** The broker owns the store and closes it, including when loading fails. */
    public AttendanceBroker(AttendanceStore store, Clock clock, PasswordHasher passwords) throws IOException {
        this(store, clock, passwords, new OnTimeEntryPolicy());
    }

    public AttendanceBroker(AttendanceStore store, Clock clock, PasswordHasher passwords,
                            AbsencePolicy absencePolicy) throws IOException {
        this.store = Objects.requireNonNull(store);
        try {
            this.clock = Objects.requireNonNull(clock);
            this.passwords = Objects.requireNonNull(passwords);
            this.absencePolicy = Objects.requireNonNull(absencePolicy);
            AttendanceSnapshot snapshot = store.load();
            accounts.putAll(snapshot.accounts());
            requests = new ArrayList<>(snapshot.requests());
            quadrants = new ArrayList<>(snapshot.quadrants());
            replaceAttendance(rebuild(requests));
        } catch (IOException | RuntimeException exception) {
            try { store.close(); }
            catch (IOException closing) { exception.addSuppressed(closing); }
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
        addUserAccount(username, password, Role.MANAGER, null);
    }

    public synchronized void registerEmployee(Session session, String username, char[] password,
                                              Employe employee) throws IOException {
        requireRole(session, Role.MANAGER);
        Objects.requireNonNull(employee);
        if (accounts.values().stream().anyMatch(a -> a.employee() != null && a.employee().id() == employee.id()))
            throw new IllegalArgumentException("El ID de empleado ya existe");
        addUserAccount(username, password, Role.EMPLOYEE, employee);
    }

    private void addUserAccount(String username, char[] password, Role role, Employe employee) throws IOException {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Indica el usuario");
        username = username.trim();
        if ("root".equals(username)) throw new IllegalArgumentException("Elige otro nombre de usuario");
        if (accounts.containsKey(username)) throw new IllegalArgumentException("El usuario ya existe");
        PasswordHasher.Credentials credentials = passwords.create(password);
        UserAccount account = new UserAccount(username, credentials.salt(), credentials.hash(), role, employee);
        var updated = new LinkedHashMap<>(accounts);
        updated.put(username, account);
        save(updated, requests);
        accounts.put(username, account);
        replaceAttendance(rebuild(requests));
    }

    public record UserInfo(String username, Role role, Employe employee) { }

    public synchronized List<UserInfo> allUsers(Session session) {
        UserAccount viewer = requireRole(session, Role.MANAGER);
        return accounts.values().stream().filter(a -> !a.username().equals("root") || viewer.username().equals("root"))
                .map(a -> new UserInfo(a.username(), a.role(), a.employee())).toList();
    }

    public synchronized boolean isRoot(Session session) {
        UserAccount user = account(session);
        return user.role() == Role.MANAGER && user.username().equals("root");
    }

    private UserAccount requireRoot(Session session) {
        if (!isRoot(session)) throw new SecurityException("No tienes permiso para esta operacion");
        return account(session);
    }

    public synchronized void resetApplication(Session session) throws IOException {
        UserAccount root = requireRoot(session);
        var remaining = new LinkedHashMap<String, UserAccount>();
        remaining.put(root.username(), root);
        save(remaining, List.of(), List.of());
        quadrants.clear();
        accounts.clear();
        accounts.putAll(remaining);
        requests = new ArrayList<>();
        replaceAttendance(rebuild(requests));
        sessions.clear();
        try {
            store.deleteBackups();
        } catch (IOException exception) {
            throw new IOException("Reset aplicado, pero no se pudieron borrar todos los backups: "
                    + exception.getMessage(), exception);
        }
    }

    public synchronized Path createBackup(Session session) throws IOException {
        requireRoot(session);
        return store.createBackup();
    }

    public synchronized void resetPassword(Session session, String username, char[] password) throws IOException {
        requireRole(session, Role.MANAGER);
        UserAccount old = accounts.get(username);
        if ("root".equals(username) && !isRoot(session))
            throw new IllegalArgumentException("Usuario desconocido");
        if (old == null) throw new IllegalArgumentException("Usuario desconocido");
        var updated = new LinkedHashMap<>(accounts);
        updated.put(username, withPassword(old, password));
        save(updated, requests);
        accounts.put(username, updated.get(username));
        sessions.entrySet().removeIf(e -> e.getValue().equals(username) && !e.getKey().equals(session.token()));
    }

    private UserAccount withPassword(UserAccount old, char[] password) {
        PasswordHasher.Credentials credentials = passwords.create(password);
        return new UserAccount(old.username(), credentials.salt(), credentials.hash(), old.role(), old.employee());
    }

    // Recovery requires local filesystem access and the exclusive broker lock.
    synchronized Map<String, String> recoverUsers() throws IOException {
        ensureOpen();
        UserAccount root = accounts.get("root");
        if (root != null && root.role() != Role.MANAGER)
            throw new IllegalStateException("El nombre root pertenece a un empleado");
        var updated = new LinkedHashMap<>(accounts);
        updated.putIfAbsent("root", new UserAccount("root", "", "", Role.MANAGER, null));
        Map<String, String> credentials = new LinkedHashMap<>();
        for (UserAccount account : List.copyOf(updated.values())) {
            String password = UUID.randomUUID().toString();
            char[] secret = password.toCharArray();
            try { updated.put(account.username(), withPassword(account, secret)); }
            finally { Arrays.fill(secret, '\0'); }
            credentials.put(account.username(), password);
        }
        if (store.exists()) {
            Path backup = store.createBackup();
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
        UserAccount account = accounts.get(username == null ? "" : username.trim());
        if (account == null || password == null || !passwords.matches(password,
                new PasswordHasher.Credentials(account.salt(), account.passwordHash())))
            throw new SecurityException("Usuario o clave incorrectos");
        Session session = new Session(UUID.randomUUID());
        sessions.put(session.token(), account.username());
        return session;
    }

    public synchronized void logout(Session session) {
        if (session != null) sessions.remove(session.token());
    }

    public synchronized Role role(Session session) { return account(session).role(); }

    public synchronized String displayName(Session session) {
        UserAccount account = account(session);
        return account.employee() == null ? account.username()
                : account.employee().name() + " " + account.employee().lastname();
    }

    public synchronized AttendanceRequest requestCheckIn(Session session, Place place) throws IOException {
        return submit(session, Objects.requireNonNull(place), AttendanceRequest.Type.CHECK_IN);
    }

    public synchronized AttendanceRequest requestCheckOut(Session session) throws IOException {
        UserAccount account = requireRole(session, Role.EMPLOYEE);
        Attendance open = attendance.history(account.employee().id()).stream().filter(Attendance::isOpen)
                .findFirst().orElseThrow(() -> new IllegalStateException("No tienes una entrada aprobada abierta"));
        return submit(session, open.place(), AttendanceRequest.Type.CHECK_OUT);
    }

    private AttendanceRequest submit(Session session, Place place, AttendanceRequest.Type type) throws IOException {
        UserAccount account = requireRole(session, Role.EMPLOYEE);
        if (requests.stream().anyMatch(r -> r.employee().id() == account.employee().id()
                && r.status() == AttendanceRequest.Status.PENDING))
            throw new IllegalStateException("Ya tienes una solicitud pendiente de revision");
        Instant now = clock.instant();
        // Validate without changing the approved history.
        try (AttendanceService validation = rebuild(requests)) {
            apply(validation, account.employee().id(), place, type, now);
        }
        AttendanceRequest request = new AttendanceRequest(UUID.randomUUID(), account.employee(), place,
                type, now, AttendanceRequest.Status.PENDING, null, null, "");
        var updated = new ArrayList<>(requests);
        updated.add(request);
        save(accounts, updated);
        requests = updated;
        return request;
    }

    public synchronized List<AttendanceRequest> myRequests(Session session) {
        int id = requireRole(session, Role.EMPLOYEE).employee().id();
        return requests.stream().filter(r -> r.employee().id() == id).toList();
    }

    public synchronized List<Attendance> myHistory(Session session) {
        return attendance.history(requireRole(session, Role.EMPLOYEE).employee().id());
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
        UserAccount manager = requireRole(session, Role.MANAGER);
        var updated = new ArrayList<>(requests);
        int index = -1;
        for (int i = 0; i < updated.size(); i++) if (updated.get(i).id().equals(requestId)) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException("Solicitud desconocida");
        AttendanceRequest old = updated.get(index);
        if (old.status() != AttendanceRequest.Status.PENDING)
            throw new IllegalStateException("La solicitud ya tiene una decision");
        updated.set(index, new AttendanceRequest(old.id(), old.employee(), old.place(), old.type(),
                old.requestedAt(), approve ? AttendanceRequest.Status.APPROVED : AttendanceRequest.Status.DENIED,
                manager.username(), clock.instant(), reason == null ? "" : reason.trim()));
        AttendanceService rebuilt = rebuild(updated);
        try { save(accounts, updated); }
        catch (IOException | RuntimeException exception) { rebuilt.close(); throw exception; }
        requests = updated;
        replaceAttendance(rebuilt);
    }

    private UserAccount account(Session session) {
        ensureOpen();
        String username = session == null ? null : sessions.get(session.token());
        if (username == null) throw new SecurityException("Inicia sesion para continuar");
        return accounts.get(username);
    }

    private UserAccount requireRole(Session session, Role role) {
        UserAccount account = account(session);
        if (account.role() != role) throw new SecurityException("No tienes permiso para esta operacion");
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
        store.close();
    }

    private AttendanceService rebuild(List<AttendanceRequest> source) {
        AttendanceService service = new AttendanceService(clock, false, absencePolicy);
        accounts.values().stream().filter(a -> a.employee() != null).forEach(a -> service.registerEmployee(a.employee()));
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
        if (accounts.values().stream().noneMatch(a -> quadrant.employe().equals(a.employee())))
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
        return attendance.missingHistory(requireRole(session, Role.EMPLOYEE).employee().id());
    }

    public synchronized List<Missing> allMissing(Session session) {
        requireRole(session, Role.MANAGER);
        return accounts.values().stream().filter(a -> a.employee() != null)
                .flatMap(a -> attendance.missingHistory(a.employee().id()).stream()).toList();
    }

    private static void apply(AttendanceService service, int id, Place place, AttendanceRequest.Type type, Instant time) {
        if (type == AttendanceRequest.Type.CHECK_IN) service.checkInAt(id, place, time);
        else service.checkOutAt(id, time);
    }

    private void save(Map<String, UserAccount> users, List<AttendanceRequest> items) throws IOException {
        save(users, items, quadrants);
    }

    private void save(Map<String, UserAccount> users, List<AttendanceRequest> items, List<Quadrant> schedules) throws IOException {
        store.save(new AttendanceSnapshot(users, items, schedules));
    }
}
