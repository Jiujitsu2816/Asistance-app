package Software.ulpgc;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QuadrantTest {
    private final Employe employee = new Employe("Ana", "Garcia", 1);
    private final Place office = new Place("Office", "Office", 0, 0);

    @Test void convertsSummerAndWinterEntryTimes() {
        Quadrant quadrant = new Quadrant(employee, office,
                List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 1)),
                List.of(LocalTime.of(9, 0), LocalTime.of(9, 0)), ZoneId.of("Atlantic/Canary"));
        assertEquals(List.of(Instant.parse("2026-07-01T08:00:00Z"),
                Instant.parse("2026-12-01T09:00:00Z")), quadrant.expectedEntries());
    }

    @Test void rejectsDateWithoutEntryTime() {
        assertThrows(IllegalArgumentException.class, () -> new Quadrant(employee, office,
                List.of(LocalDate.of(2026, 10, 27)), List.of(), ZoneOffset.UTC));
    }

    @Test void scheduleIsProtectedFromExternalChanges() {
        var dates = new ArrayList<>(List.of(LocalDate.of(2026, 10, 27)));
        var times = new ArrayList<>(List.of(LocalTime.of(9, 0)));
        Quadrant quadrant = new Quadrant(employee, office, dates, times, ZoneOffset.UTC);
        dates.clear();
        times.clear();
        assertEquals(List.of(Instant.parse("2026-10-27T09:00:00Z")), quadrant.expectedEntries());
        assertThrows(UnsupportedOperationException.class, () -> quadrant.labor().clear());
        assertThrows(UnsupportedOperationException.class, () -> quadrant.timetable().clear());
    }

    @Test void absenceDateIsProtectedFromExternalChanges() {
        Date entry = new Date(1000);
        Missing absence = new Missing(employee, office, entry);
        entry.setTime(2000);
        absence.date().setTime(3000);
        assertEquals(new Date(1000), absence.date());
    }
}
