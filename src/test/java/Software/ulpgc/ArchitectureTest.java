package Software.ulpgc;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises dependency substitution and the persistence boundaries used by the broker. */
class ArchitectureTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private final char[] password = "test-password".toCharArray();
    private final Employe employee = new Employe("Ana", "Garcia", 1);
    private final Place office = new Place("Office", "Office", 0, 0);

    @Test void failedSaveDoesNotPublishAccountsRequestsOrSchedules() throws Exception {
        var store = new MemoryStore();
        try (var broker = new AttendanceBroker(store, clock, new Pbkdf2PasswordHasher())) {
            broker.createFirstManager("manager", password);
            var manager = broker.login("manager", password);
            var original = store.snapshot;
            store.failSave = true;
            assertThrows(IOException.class, () -> broker.registerEmployee(manager, "ana", password, employee));
            assertEquals(1, broker.allUsers(manager).size());
            assertThrows(SecurityException.class, () -> broker.login("ana", password));
            assertSame(original, store.snapshot);

            store.failSave = false;
            broker.registerEmployee(manager, "ana", password, employee);
            var ana = broker.login("ana", password);
            original = store.snapshot;
            store.failSave = true;
            assertThrows(IOException.class, () -> broker.requestCheckIn(ana, office));
            assertTrue(broker.myRequests(ana).isEmpty());
            assertThrows(IOException.class, () -> broker.registerQuadrant(manager, shift()));
            assertTrue(broker.allQuadrants(manager).isEmpty());
            assertTrue(broker.myMissing(ana).isEmpty());
            assertSame(original, store.snapshot);
        }
        assertTrue(store.closed);
    }

    @Test void failedLoadReleasesOwnedStore() {
        var store = new MemoryStore();
        store.failLoad = true;
        assertThrows(IOException.class, () -> new AttendanceBroker(store, clock, new Pbkdf2PasswordHasher()));
        assertTrue(store.closed);
    }

    @Test void policyCanBeChangedThroughBrokerComposition() throws Exception {
        var store = new MemoryStore();
        AbsencePolicy covered = (expected, zone, history) -> true;
        try (var broker = new AttendanceBroker(store, clock, new Pbkdf2PasswordHasher(), covered)) {
            broker.createFirstManager("manager", password);
            var manager = broker.login("manager", password);
            broker.registerEmployee(manager, "ana", password, employee);
            broker.registerQuadrant(manager, shift());
            assertTrue(broker.allMissing(manager).isEmpty());
        }
        // Replaying the same data with the normal rule detects the missed start.
        try (var broker = new AttendanceBroker(new MemoryStore(store.snapshot), clock, new Pbkdf2PasswordHasher())) {
            var manager = broker.login("manager", password);
            assertEquals(1, broker.allMissing(manager).size());
        }
    }

    @Test void brokerUsesSuppliedPasswordHasherForCreationAndLogin() throws Exception {
        var store = new MemoryStore();
        var delegate = new Pbkdf2PasswordHasher();
        int[] calls = {0, 0};
        PasswordHasher hasher = new PasswordHasher() {
            public Credentials create(char[] secret) { calls[0]++; return delegate.create(secret); }
            public boolean matches(char[] secret, Credentials credentials) {
                calls[1]++;
                return delegate.matches(secret, credentials);
            }
        };
        try (var broker = new AttendanceBroker(store, clock, hasher)) {
            broker.createFirstManager("manager", password);
            broker.login("manager", password);
            assertArrayEquals(new int[]{1, 1}, calls);
        }
    }

    @Test void snapshotsAreImmutableAndKeepAccountOrder() {
        var accounts = new LinkedHashMap<String, UserAccount>();
        accounts.put("manager", new UserAccount("manager", "salt", "hash", AttendanceBroker.Role.MANAGER, null));
        accounts.put("ana", new UserAccount("ana", "salt", "hash", AttendanceBroker.Role.EMPLOYEE, employee));
        var shifts = new ArrayList<>(List.of(shift()));
        var snapshot = new AttendanceSnapshot(accounts, List.of(), shifts);
        accounts.clear();
        shifts.clear();
        assertEquals(List.of("manager", "ana"), new ArrayList<>(snapshot.accounts().keySet()));
        assertEquals(1, snapshot.quadrants().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.accounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.requests().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.quadrants().clear());
    }

    @Test void codecReadsLegacyEntryOnlySchedule() throws Exception {
        var codec = new AttendancePropertiesCodec();
        var properties = codec.encode(new AttendanceSnapshot(Map.of(), List.of(), List.of(shift())));
        properties.remove("quadrant.0.end.0");
        var loaded = codec.decode(properties).quadrants().getFirst();
        assertTrue(loaded.endTimes().isEmpty());
        assertEquals(shift().expectedEntries(), loaded.expectedEntries());
    }

    @Test void storeCloseIsIdempotentAndDisallowsUnlockedAccess() throws Exception {
        var file = Files.createTempDirectory(Path.of("target"), "store-close-").resolve("attendance.properties");
        var store = new PropertiesAttendanceStore(file, clock);
        store.close();
        assertDoesNotThrow(store::close);
        assertThrows(IllegalStateException.class, store::load);
        assertThrows(IllegalStateException.class, () -> store.save(new AttendanceSnapshot(Map.of(), List.of(), List.of())));
        assertThrows(IllegalStateException.class, store::createBackup);
        assertThrows(IllegalStateException.class, store::deleteBackups);
        try (var reopened = new PropertiesAttendanceStore(file, clock)) {
            assertFalse(reopened.exists());
        }
    }

    private Quadrant shift() {
        return new Quadrant(employee, office, List.of(LocalDate.of(2026, 10, 27)),
                List.of(LocalTime.of(9, 0)), List.of(LocalTime.of(17, 0)), ZoneOffset.UTC);
    }

    private static final class MemoryStore implements AttendanceStore {
        AttendanceSnapshot snapshot;
        boolean failSave, failLoad, closed;
        MemoryStore() { this(new AttendanceSnapshot(Map.of(), List.of(), List.of())); }
        MemoryStore(AttendanceSnapshot snapshot) { this.snapshot = snapshot; }
        public AttendanceSnapshot load() throws IOException {
            if (failLoad) throw new IOException("Simulated load failure");
            return snapshot;
        }
        public void save(AttendanceSnapshot snapshot) throws IOException {
            if (failSave) throw new IOException("Simulated save failure");
            this.snapshot = snapshot;
        }
        public boolean exists() { return !snapshot.accounts().isEmpty(); }
        public Path createBackup() { return Path.of("target", "memory.backup"); }
        public void deleteBackups() { }
        public void close() { closed = true; }
    }
}
