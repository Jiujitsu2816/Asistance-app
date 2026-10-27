package Software.ulpgc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Regression coverage for the complete scheduled-absence flow. */
class MissingTest {
    @org.junit.jupiter.api.Test
    void scheduledAbsenceRegression() {
        main(new String[0]);
    }

    public static void main(String[] args) {
        MutableClock clock = new MutableClock();
        Place office = new Place("Office", "Office", 0, 0);
        Instant deadline = Instant.parse("2026-10-27T09:00:00Z");
        try (AttendanceService service = new AttendanceService(clock)) {
            for (int id = 1; id <= 4; id++)
                service.registerEmployee(new Employe("Employee", "Test", id));
            // Yesterday's open session must not count for today's deadline.
            clock.now = deadline.minusSeconds(86400);
            service.checkIn(4, office);
            clock.now = deadline.minusSeconds(60);
            for (int id = 1; id <= 4; id++)
                service.registerQuadrant(new Quadrant(new Employe("Employee", "Test", id), office,
                        List.of(LocalDate.of(2026, 10, 27)), List.of(LocalTime.of(9, 0)),
                        ZoneId.of("Atlantic/Canary")));
            assert service.missingHistory(1).isEmpty() : "Premature absence";
            service.checkIn(2, office);
            service.checkOut(2);
            service.checkIn(3, new Place("Other", "Office", 1, 1));
            clock.now = deadline;
            service.checkMissing();
            assert service.missingHistory(1).size() == 1 : "Missing absence at deadline";
            assert service.missingHistory(2).isEmpty() : "Closed session should count";
            assert service.missingHistory(3).size() == 1 : "Wrong workplace should not count";
            assert service.missingHistory(4).size() == 1 : "Yesterday should not count";
            service.scheduleMissing(1, office, deadline);
            service.checkMissing();
            assert service.missingHistory(1).size() == 1 : "Duplicate absence";
            clock.now = deadline.plusSeconds(60);
            service.checkIn(1, office);
            assert service.missingHistory(1).size() == 1 : "Late check-in erased absence";
            assert service.missingHistory(1).getFirst().date().toInstant().equals(deadline)
                    : "Absence must store scheduled entry time";
        }
        System.out.println("Missing tests passed");
    }

    private static class MutableClock extends Clock {
        private volatile Instant now = Instant.EPOCH;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
