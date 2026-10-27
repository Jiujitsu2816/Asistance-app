package Software.ulpgc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceServiceTest {
    private final Employe employee = new Employe("Ana", "Garcia", 1);
    private final Place office = new Place("Office", "Office", 0, 0);
    private final Instant entry = Instant.parse("2026-10-27T09:00:00Z");
    private MutableClock clock;
    private AttendanceService service;

    @BeforeEach void setUp() {
        clock = new MutableClock(entry.minusSeconds(60));
        service = new AttendanceService(clock);
        service.registerEmployee(employee);
    }

    @AfterEach void tearDown() { service.close(); }

    @Test void rejectsDuplicateEmployeeId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerEmployee(new Employe("Other", "Name", 1)));
        assertEquals(employee, service.checkIn(1, office).employee());
    }

    @Test void rejectsUnknownEmployeesAcrossOperations() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> service.checkIn(99, office)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.checkOut(99)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.history(99)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.missingHistory(99)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.scheduleMissing(99, office, entry)));
    }

    @Test void recordsEntryExitAndWorkedDuration() {
        Attendance open = service.checkIn(1, office);
        assertEquals(clock.instant(), open.checkIn());
        assertEquals(office, open.place());
        assertTrue(open.isOpen());
        assertThrows(IllegalStateException.class, open::workedDuration);
        clock.advance(Duration.ofHours(8));
        Attendance closed = service.checkOut(1);
        assertFalse(closed.isOpen());
        assertEquals(clock.instant(), closed.checkOut());
        assertEquals(Duration.ofHours(8), closed.workedDuration());
        assertEquals(List.of(closed), service.history(1));
    }

    @Test void rejectsDoubleCheckInWithoutChangingHistory() {
        Attendance first = service.checkIn(1, office);
        assertThrows(IllegalStateException.class, () -> service.checkIn(1, office));
        assertEquals(List.of(first), service.history(1));
    }

    @Test void rejectsCheckOutWithoutOpenSession() {
        assertThrows(IllegalStateException.class, () -> service.checkOut(1));
        service.checkIn(1, office);
        service.checkOut(1);
        assertThrows(IllegalStateException.class, () -> service.checkOut(1));
    }

    @Test void supportsMultipleSessionsAndSeparatesEmployees() {
        service.registerEmployee(new Employe("Luis", "Perez", 2));
        service.checkIn(1, office);
        service.checkIn(2, office);
        service.checkOut(1);
        service.checkIn(1, office);
        assertEquals(2, service.history(1).size());
        assertEquals(1, service.history(2).size());
        assertTrue(service.history(2).getFirst().isOpen());
    }

    @Test void historyIsAnImmutableSnapshot() {
        List<Attendance> before = service.history(1);
        service.checkIn(1, office);
        assertTrue(before.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> service.history(1).clear());
    }

    @Test void rejectsNullInputs() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new AttendanceService(null)),
                () -> assertThrows(NullPointerException.class, () -> service.registerEmployee(null)),
                () -> assertThrows(NullPointerException.class, () -> service.checkIn(1, null)),
                () -> assertThrows(NullPointerException.class, () -> service.registerQuadrant(null)),
                () -> assertThrows(NullPointerException.class, () -> service.scheduleMissing(1, office, null)));
        assertTrue(service.history(1).isEmpty());
    }

    @Test void absenceAppearsAtScheduledEntryAndStoresExpectedTime() {
        service.scheduleMissing(1, office, entry);
        assertTrue(service.missingHistory(1).isEmpty());
        clock.advance(Duration.ofMinutes(1));
        service.checkMissing();
        Missing absence = service.missingHistory(1).getFirst();
        assertEquals(employee, absence.employe());
        assertEquals(office, absence.place());
        assertEquals(entry, absence.date().toInstant());
    }

    @Test void timelyCheckInPreventsAbsenceEvenAfterCheckOut() {
        service.scheduleMissing(1, office, entry);
        service.checkIn(1, office);
        service.checkOut(1);
        clock.advance(Duration.ofHours(2));
        assertTrue(service.missingHistory(1).isEmpty());
    }

    @Test void lateCheckInPreservesAbsenceAndExpectedEntryTime() {
        service.scheduleMissing(1, office, entry);
        clock.advance(Duration.ofMinutes(10));
        service.checkIn(1, office);
        assertEquals(1, service.missingHistory(1).size());
        assertEquals(entry, service.missingHistory(1).getFirst().date().toInstant());
    }

    @Test void repeatedChecksAndScheduleRegistrationDoNotDuplicateAbsence() {
        service.scheduleMissing(1, office, entry);
        service.scheduleMissing(1, office, entry);
        clock.advance(Duration.ofHours(1));
        service.checkMissing();
        service.scheduleMissing(1, office, entry);
        service.checkMissing();
        assertEquals(1, service.missingHistory(1).size());
    }

    @Test void checkInAtAnotherWorkplaceDoesNotPreventAbsence() {
        service.checkIn(1, new Place("Other", "Office", 1, 1));
        service.scheduleMissing(1, office, entry);
        clock.advance(Duration.ofMinutes(1));
        assertEquals(1, service.missingHistory(1).size());
    }

    @Test void previousDaysOpenSessionDoesNotPreventAbsence() {
        clock.advance(Duration.ofDays(-1));
        service.checkIn(1, office);
        service.scheduleMissing(1, office, entry);
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        assertEquals(1, service.missingHistory(1).size());
    }

    @Test void absencesAreSeparatedByEmployee() {
        service.registerEmployee(new Employe("Luis", "Perez", 2));
        service.scheduleMissing(1, office, entry);
        clock.advance(Duration.ofHours(1));
        assertEquals(1, service.missingHistory(1).size());
        assertTrue(service.missingHistory(2).isEmpty());
    }

    @Test void quadrantRegistersMultipleWorkingDays() {
        service.registerQuadrant(new Quadrant(employee, office,
                List.of(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)),
                List.of(LocalTime.of(9, 0), LocalTime.of(10, 0)), ZoneOffset.UTC));
        clock.advance(Duration.ofMinutes(1));
        assertEquals(1, service.missingHistory(1).size());
        clock.advance(Duration.ofDays(1).plusHours(1));
        assertEquals(List.of(entry, entry.plus(Duration.ofDays(1).plusHours(1))),
                service.missingHistory(1).stream().map(m -> m.date().toInstant()).toList());
    }

    @Test void quadrantUsesItsZoneForBothEntryTimeAndWorkingDay() {
        // In Tokyo both instants are on Oct 28, although their UTC dates differ.
        clock.now = Instant.parse("2026-10-27T23:30:00Z");
        service.checkIn(1, office);
        service.registerQuadrant(new Quadrant(employee, office,
                List.of(LocalDate.of(2026, 10, 28)), List.of(LocalTime.of(9, 0)),
                ZoneId.of("Asia/Tokyo")));
        clock.now = Instant.parse("2026-10-28T00:00:00Z");
        assertTrue(service.missingHistory(1).isEmpty());
    }

    @Test void quadrantRejectsUnknownOrMismatchedEmployee() {
        for (Employe invalid : List.of(new Employe("Unknown", "Employee", 99),
                new Employe("Wrong", "Name", 1))) {
            assertThrows(IllegalArgumentException.class, () -> service.registerQuadrant(
                    new Quadrant(invalid, office, List.of(LocalDate.of(2026, 10, 27)),
                            List.of(LocalTime.of(9, 0)), ZoneOffset.UTC)));
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        private MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
