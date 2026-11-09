package Software.ulpgc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;

/** Dependency-free integration tests. Run using scripts/test.ps1. */
public final class AttendanceBrokerTest {
    private static final char[] PASSWORD = "test-password".toCharArray();
    private static final Place OFFICE = new Place("Oficina", "Oficina", 0, 0);
    private static int passed;

    public static void main(String[] args) throws Exception {
        test("Configuracion inicial y autenticacion", AttendanceBrokerTest::authentication);
        test("Recuperacion de usuarios y restablecimiento", AttendanceBrokerTest::recovery);
        test("Root oculto y reset persistente", AttendanceBrokerTest::rootReset);
        test("Rotacion de backups", AttendanceBrokerTest::backupRetention);
        test("Reset fallido conserva cuentas y sesiones", AttendanceBrokerTest::failedReset);
        test("Permisos y cierre de sesion", AttendanceBrokerTest::authorization);
        test("Validacion del alta de empleados", AttendanceBrokerTest::registration);
        test("Solicitud pendiente separada del registro aprobado", AttendanceBrokerTest::pending);
        test("Aprobacion conserva la hora original", AttendanceBrokerTest::approval);
        test("Denegacion conserva el motivo sin fichar", AttendanceBrokerTest::denial);
        test("Entrada, salida y suma de horas", AttendanceBrokerTest::workedHours);
        test("Solo se consulta el registro propio", AttendanceBrokerTest::privacy);
        test("Solicitudes duplicadas y fichajes invalidos", AttendanceBrokerTest::invalidRequests);
        test("Una decision es definitiva", AttendanceBrokerTest::finalDecision);
        test("Persistencia tras reinicio", AttendanceBrokerTest::restart);
        test("Un fallo al guardar no cambia la decision", AttendanceBrokerTest::failedSave);
        test("Un segundo proceso no sobrescribe el registro", AttendanceBrokerTest::fileLock);
        test("Registro corrupto no se sustituye", AttendanceBrokerTest::corruptFile);
        test("Las listas del registro son inmutables", AttendanceBrokerTest::immutableHistory);
        System.out.println("OK: " + passed + " pruebas");
    }

    private static void rootReset() throws Exception {
        try (Fixture f = new Fixture()) {
            approveEntry(f);
            var credentials = f.broker.recoverUsers();
            var root = f.broker.login("root", credentials.get("root").toCharArray());
            var manager = f.broker.login("responsable", credentials.get("responsable").toCharArray());
            var employee = f.broker.login("ana", credentials.get("ana").toCharArray());
            equal(false, f.broker.needsSetup());
            equal(2, f.broker.allUsers(manager).size());
            check(f.broker.allUsers(manager).stream().noneMatch(u -> u.username().equals("root")), "Root oculto");
            equal(3, f.broker.allUsers(root).size());
            expect(IllegalArgumentException.class, () -> f.broker.resetPassword(manager, "root", PASSWORD));
            expect(SecurityException.class, () -> f.broker.resetApplication(manager));
            expect(SecurityException.class, () -> f.broker.resetApplication(employee));
            expect(SecurityException.class, () -> f.broker.resetApplication(null));
            Path unrelated = f.file.resolveSibling("otro.properties.backup-conservar");
            Files.writeString(unrelated, "conservar");
            f.broker.resetApplication(root);
            equal(true, f.broker.needsSetup());
            expect(SecurityException.class, () -> f.broker.allUsers(root));
            expect(SecurityException.class, () -> f.broker.allUsers(manager));
            expect(SecurityException.class, () -> f.broker.myRequests(employee));
            equal(0L, backupCount(f.file));
            check(Files.exists(unrelated), "No se borran copias de otros registros");
            f.broker.close();
            try (var reopened = new AttendanceBroker(f.file, f.clock)) {
                equal(true, reopened.needsSetup());
                var rootAgain = reopened.login("root", credentials.get("root").toCharArray());
                equal(1, reopened.allUsers(rootAgain).size());
                equal(List.of(), reopened.allRequests(rootAgain));
                expect(SecurityException.class, () -> reopened.login("ana", credentials.get("ana").toCharArray()));
                expect(IllegalArgumentException.class, () -> reopened.createFirstManager(" root ", PASSWORD));
                reopened.createFirstManager("nuevo", PASSWORD);
                equal(false, reopened.needsSetup());
                var newManager = reopened.login("nuevo", PASSWORD);
                equal(1, reopened.allUsers(newManager).size());
                expect(IllegalStateException.class, () -> reopened.createFirstManager("otro", PASSWORD));
                expect(IllegalArgumentException.class, () -> reopened.registerEmployee(newManager, "root", PASSWORD,
                        new Employe("Otro", "Usuario", 1)));
                reopened.registerEmployee(newManager, "empleado", PASSWORD, new Employe("Nuevo", "Empleado", 1));
                reopened.requestCheckIn(reopened.login("empleado", PASSWORD), OFFICE);
            }
        }
    }

    private static long backupCount(Path file) throws IOException {
        try (var paths = Files.list(file.getParent())) {
            return paths.filter(p -> p.getFileName().toString().startsWith(file.getFileName() + ".backup-")).count();
        }
    }

    private static void backupRetention() throws Exception {
        try (Fixture f = new Fixture()) {
            var credentials = f.broker.recoverUsers();
            var root = f.broker.login("root", credentials.get("root").toCharArray());
            for (int i = 0; i < 4; i++) {
                Path old = f.file.resolveSibling(f.file.getFileName() + ".backup-old-" + i);
                Files.writeString(old, "old");
                Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(i));
            }
            Path first = f.broker.createBackup(root);
            equal(2L, backupCount(f.file));
            equal(Files.readString(f.file), Files.readString(first));
            // Force deterministic ordering without sleeps.
            try (var paths = Files.list(f.file.getParent())) {
                for (Path p : paths.filter(p -> p.getFileName().toString().contains(".backup-")).toList())
                    Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.fromMillis(p.equals(first) ? 2 : 1));
            }
            Path second = f.broker.createBackup(root);
            equal(2L, backupCount(f.file));
            check(Files.exists(first) && Files.exists(second), "Se conservan la copia nueva y la anterior");
            f.broker.recoverUsers();
            equal(2L, backupCount(f.file));
        }
    }

    private static void failedReset() throws Exception {
        try (Fixture f = new Fixture()) {
            var credentials = f.broker.recoverUsers();
            var root = f.broker.login("root", credentials.get("root").toCharArray());
            long backups = backupCount(f.file);
            Path saved = f.file.resolveSibling("saved.properties");
            Files.move(f.file, saved);
            Files.createDirectory(f.file);
            Files.writeString(f.file.resolve("blocker"), "test");
            expect(IOException.class, () -> f.broker.resetApplication(root));
            equal(3, f.broker.allUsers(root).size());
            equal(backups, backupCount(f.file));
            equal(false, f.broker.needsSetup());
            Files.delete(f.file.resolve("blocker"));
            Files.delete(f.file);
            Files.move(saved, f.file);
        }
    }

    private static void recovery() throws Exception {
        try (Fixture f = new Fixture()) {
            approveEntry(f);
            var history = f.broker.myHistory(f.employee);
            expect(SecurityException.class, () -> f.broker.allUsers(f.employee));
            expect(SecurityException.class, () -> f.broker.resetPassword(f.employee, "ana", PASSWORD));
            expect(IllegalArgumentException.class, () -> f.broker.resetPassword(f.manager, "ana", new char[0]));
            f.broker.resetPassword(f.manager, "ana", "nueva-clave".toCharArray());
            expect(SecurityException.class, () -> f.broker.myHistory(f.employee));
            expect(SecurityException.class, () -> f.broker.login("ana", PASSWORD));
            equal(history, f.broker.myHistory(f.broker.login("ana", "nueva-clave".toCharArray())));
            var credentials = f.broker.recoverUsers();
            expect(SecurityException.class, () -> f.broker.allUsers(f.manager));
            f.broker.close();
            try (AttendanceBroker reopened = new AttendanceBroker(f.file, f.clock)) {
                for (var entry : credentials.entrySet()) reopened.login(entry.getKey(), entry.getValue().toCharArray());
                var root = reopened.login("root", credentials.get("root").toCharArray());
                equal(3, reopened.allUsers(root).size());
                equal(history, reopened.myHistory(reopened.login("ana", credentials.get("ana").toCharArray())));
                String stored = Files.readString(f.file);
                for (String password : credentials.values()) check(!stored.contains(password), "Clave protegida");
            }
        }
    }

    private static void authentication() throws Exception {
        try (Fixture f = new Fixture()) {
            expect(SecurityException.class, () -> f.broker.login("ana", "incorrecta".toCharArray()));
            expect(SecurityException.class, () -> f.broker.login("desconocido", PASSWORD));
            expect(IllegalStateException.class, () -> f.broker.createFirstManager("intruso", PASSWORD));
            equal(AttendanceBroker.Role.MANAGER, f.broker.role(f.manager));
            equal(AttendanceBroker.Role.EMPLOYEE, f.broker.role(f.employee));
            equal("Ana Garcia", f.broker.displayName(f.employee));
        }
    }

    private static void authorization() throws Exception {
        try (Fixture f = new Fixture()) {
            expect(SecurityException.class, () -> f.broker.allRequests(f.employee));
            expect(SecurityException.class, () -> f.broker.pendingRequests(f.employee));
            expect(SecurityException.class, () -> f.broker.registerEmployee(f.employee, "otra", PASSWORD,
                    new Employe("Otra", "Persona", 2)));
            expect(SecurityException.class, () -> f.broker.requestCheckIn(f.manager, OFFICE));
            expect(SecurityException.class, () -> f.broker.myHistory(f.manager));
            expect(SecurityException.class, () -> f.broker.myHistory(new AttendanceBroker.Session(UUID.randomUUID())));
            expect(SecurityException.class, () -> f.broker.myHistory(null));
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            expect(SecurityException.class, () -> f.broker.decide(f.employee, request.id(), true, ""));
            f.broker.logout(f.employee);
            expect(SecurityException.class, () -> f.broker.myRequests(f.employee));
        }
    }

    private static void registration() throws Exception {
        try (Fixture f = new Fixture()) {
            expect(IllegalArgumentException.class, () -> f.broker.registerEmployee(f.manager, "ana", PASSWORD,
                    new Employe("Otra", "Persona", 2)));
            expect(IllegalArgumentException.class, () -> f.broker.registerEmployee(f.manager, "otra", PASSWORD,
                    new Employe("Otra", "Persona", 1)));
            expect(IllegalArgumentException.class, () -> f.broker.registerEmployee(f.manager, "otra", "123".toCharArray(),
                    new Employe("Otra", "Persona", 2)));
            expect(SecurityException.class, () -> f.broker.login("otra", PASSWORD));
        }
    }

    private static void pending() throws Exception {
        try (Fixture f = new Fixture()) {
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            equal(f.clock.now, request.requestedAt());
            equal(AttendanceRequest.Status.PENDING, request.status());
            equal(List.of(request), f.broker.myRequests(f.employee));
            equal(List.of(request), f.broker.pendingRequests(f.manager));
            equal(List.of(), f.broker.myHistory(f.employee));
            equal(Duration.ZERO, f.broker.myWorkedHours(f.employee));
        }
    }

    private static void approval() throws Exception {
        try (Fixture f = new Fixture()) {
            Instant original = f.clock.now;
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            f.clock.advance(Duration.ofHours(2));
            f.broker.decide(f.manager, request.id(), true, "Revisado");
            AttendanceRequest approved = f.broker.myRequests(f.employee).getFirst();
            equal(original, approved.requestedAt());
            equal(f.clock.now, approved.decidedAt());
            equal("responsable", approved.decidedBy());
            equal(AttendanceRequest.Status.APPROVED, approved.status());
            equal(original, f.broker.myHistory(f.employee).getFirst().checkIn());
            equal(List.of(), f.broker.pendingRequests(f.manager));
        }
    }

    private static void denial() throws Exception {
        try (Fixture f = new Fixture()) {
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            expect(IllegalArgumentException.class, () -> f.broker.decide(f.manager, request.id(), false, "  "));
            equal(1, f.broker.pendingRequests(f.manager).size());
            f.broker.decide(f.manager, request.id(), false, "No corresponde al turno");
            equal(AttendanceRequest.Status.DENIED, f.broker.myRequests(f.employee).getFirst().status());
            equal("No corresponde al turno", f.broker.myRequests(f.employee).getFirst().reason());
            equal(List.of(), f.broker.myHistory(f.employee));
            f.broker.requestCheckIn(f.employee, OFFICE);
            equal(2, f.broker.myRequests(f.employee).size());
        }
    }

    private static void workedHours() throws Exception {
        try (Fixture f = new Fixture()) {
            approveEntry(f);
            f.clock.advance(Duration.ofHours(8).plusMinutes(15));
            Instant requestedExit = f.clock.now;
            AttendanceRequest exit = f.broker.requestCheckOut(f.employee);
            equal(Duration.ZERO, f.broker.myWorkedHours(f.employee));
            f.clock.advance(Duration.ofHours(1));
            f.broker.decide(f.manager, exit.id(), true, "");
            equal(requestedExit, f.broker.myHistory(f.employee).getFirst().checkOut());
            equal(Duration.ofHours(8).plusMinutes(15), f.broker.myWorkedHours(f.employee));
            approveEntry(f);
            f.clock.advance(Duration.ofHours(1));
            AttendanceRequest secondExit = f.broker.requestCheckOut(f.employee);
            f.broker.decide(f.manager, secondExit.id(), true, "");
            equal(Duration.ofHours(9).plusMinutes(15), f.broker.myWorkedHours(f.employee));
        }
    }

    private static void privacy() throws Exception {
        try (Fixture f = new Fixture()) {
            f.broker.registerEmployee(f.manager, "luis", PASSWORD, new Employe("Luis", "Perez", 2));
            AttendanceBroker.Session other = f.broker.login("luis", PASSWORD);
            approveEntry(f);
            equal(List.of(), f.broker.myHistory(other));
            equal(List.of(), f.broker.myRequests(other));
            f.broker.requestCheckIn(other, OFFICE);
            equal(1, f.broker.myRequests(f.employee).size());
            equal(1, f.broker.myRequests(other).size());
            equal(2, f.broker.allRequests(f.manager).size());
        }
    }

    private static void invalidRequests() throws Exception {
        try (Fixture f = new Fixture()) {
            expect(IllegalStateException.class, () -> f.broker.requestCheckOut(f.employee));
            AttendanceRequest entry = f.broker.requestCheckIn(f.employee, OFFICE);
            expect(IllegalStateException.class, () -> f.broker.requestCheckIn(f.employee, OFFICE));
            expect(IllegalStateException.class, () -> f.broker.requestCheckOut(f.employee));
            f.broker.decide(f.manager, entry.id(), true, "");
            expect(IllegalStateException.class, () -> f.broker.requestCheckIn(f.employee, OFFICE));
            AttendanceRequest exit = f.broker.requestCheckOut(f.employee);
            expect(IllegalStateException.class, () -> f.broker.requestCheckOut(f.employee));
            f.broker.decide(f.manager, exit.id(), false, "Salida incorrecta");
            check(f.broker.myHistory(f.employee).getFirst().isOpen(), "Una salida denegada no cierra la sesion");
            f.broker.requestCheckOut(f.employee);
        }
    }

    private static void finalDecision() throws Exception {
        try (Fixture f = new Fixture()) {
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            f.broker.decide(f.manager, request.id(), true, "");
            expect(IllegalStateException.class, () -> f.broker.decide(f.manager, request.id(), false, "Cambio"));
            expect(IllegalArgumentException.class, () -> f.broker.decide(f.manager, UUID.randomUUID(), true, ""));
            equal(1, f.broker.myHistory(f.employee).size());
        }
    }

    private static void restart() throws Exception {
        try (Fixture f = new Fixture()) {
            approveEntry(f);
            f.clock.advance(Duration.ofHours(8));
            AttendanceRequest denied = f.broker.requestCheckOut(f.employee);
            f.broker.decide(f.manager, denied.id(), false, "Comprobar hora");
            AttendanceRequest pending = f.broker.requestCheckOut(f.employee);
            List<AttendanceRequest> saved = f.broker.allRequests(f.manager);
            f.broker.close();
            try (AttendanceBroker reopened = new AttendanceBroker(f.file, f.clock)) {
                AttendanceBroker.Session manager = reopened.login("responsable", PASSWORD);
                AttendanceBroker.Session employee = reopened.login("ana", PASSWORD);
                equal(saved, reopened.allRequests(manager));
                equal(pending.id(), reopened.pendingRequests(manager).getFirst().id());
                expect(SecurityException.class, () -> reopened.myHistory(f.employee));
                reopened.decide(manager, pending.id(), true, "");
                equal(Duration.ofHours(8), reopened.myWorkedHours(employee));
                String stored = Files.readString(f.file);
                check(!stored.contains("test-password"), "La clave no se guarda en texto plano");
            }
        }
    }

    private static void failedSave() throws Exception {
        try (Fixture f = new Fixture()) {
            AttendanceRequest request = f.broker.requestCheckIn(f.employee, OFFICE);
            // Replace the destination with a nonempty directory to force a portable write failure.
            Path backup = f.file.resolveSibling("backup.properties");
            Files.move(f.file, backup);
            Files.createDirectory(f.file);
            Files.writeString(f.file.resolve("blocker"), "test");
            expect(IOException.class, () -> f.broker.decide(f.manager, request.id(), true, ""));
            equal(AttendanceRequest.Status.PENDING, f.broker.myRequests(f.employee).getFirst().status());
            equal(List.of(), f.broker.myHistory(f.employee));
            Files.delete(f.file.resolve("blocker"));
            Files.delete(f.file);
            Files.move(backup, f.file);
            f.broker.decide(f.manager, request.id(), true, "");
        }
    }

    private static void fileLock() throws Exception {
        try (Fixture f = new Fixture()) {
            expect(IOException.class, () -> {
                try (AttendanceBroker ignored = new AttendanceBroker(f.file, f.clock)) {
                    throw new AssertionError("No debe abrirse dos veces");
                }
            });
            approveEntry(f);
        }
    }

    private static void corruptFile() throws Exception {
        Path folder = newTestDirectory();
        Path file = folder.resolve("bad.properties");
        Files.writeString(file, "version=desconocida");
        expect(IOException.class, () -> new AttendanceBroker(file, Clock.systemUTC()));
        equal("version=desconocida", Files.readString(file));
    }

    private static void immutableHistory() throws Exception {
        try (Fixture f = new Fixture()) {
            List<AttendanceRequest> before = f.broker.myRequests(f.employee);
            approveEntry(f);
            equal(List.of(), before);
            expect(UnsupportedOperationException.class, () -> f.broker.myRequests(f.employee).clear());
            expect(UnsupportedOperationException.class, () -> f.broker.myHistory(f.employee).clear());
            expect(UnsupportedOperationException.class, () -> f.broker.allRequests(f.manager).clear());
        }
    }

    private static void approveEntry(Fixture f) throws IOException {
        AttendanceRequest entry = f.broker.requestCheckIn(f.employee, OFFICE);
        f.broker.decide(f.manager, entry.id(), true, "");
    }

    private static Path newTestDirectory() throws IOException {
        Path root = Path.of("target", "broker-tests");
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "run-");
    }

    private static final class Fixture implements AutoCloseable {
        final MutableClock clock = new MutableClock();
        final Path file;
        final AttendanceBroker broker;
        final AttendanceBroker.Session manager;
        final AttendanceBroker.Session employee;
        Fixture() throws Exception {
            file = newTestDirectory().resolve("attendance.properties");
            broker = new AttendanceBroker(file, clock);
            check(broker.needsSetup(), "Debe solicitar configuracion inicial");
            broker.createFirstManager("responsable", PASSWORD);
            manager = broker.login("responsable", PASSWORD);
            broker.registerEmployee(manager, "ana", PASSWORD, new Employe("Ana", "Garcia", 1));
            employee = broker.login("ana", PASSWORD);
        }
        @Override public void close() throws IOException { broker.close(); }
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-27T09:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void test(String name, Action action) throws Exception {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("Esperado: " + expected + "; recibido: " + actual);
    }
    private static void expect(Class<? extends Throwable> type, Action action) throws Exception {
        try { action.run(); }
        catch (Throwable exception) {
            if (type.isInstance(exception)) return;
            throw new AssertionError("Se esperaba " + type.getSimpleName(), exception);
        }
        throw new AssertionError("Falta excepcion " + type.getSimpleName());
    }
}
