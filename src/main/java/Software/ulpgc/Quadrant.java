package Software.ulpgc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Each working date has an entry time at the same index in timetable. */
public record Quadrant(Employe employe, Place place, List<LocalDate> labor,
                       List<LocalTime> timetable, List<LocalTime> endTimes, ZoneId zone) {
    /** Compatibility for existing entry-only schedules. */
    public Quadrant(Employe employe, Place place, List<LocalDate> labor,
                    List<LocalTime> timetable, ZoneId zone) {
        this(employe, place, labor, timetable, List.of(), zone);
    }

    public Quadrant {
        Objects.requireNonNull(employe, "Employee is required");
        Objects.requireNonNull(place, "Place is required");
        Objects.requireNonNull(zone, "Time zone is required");
        labor = List.copyOf(labor);
        timetable = List.copyOf(timetable);
        endTimes = List.copyOf(endTimes);
        if (labor.size() != timetable.size())
            throw new IllegalArgumentException("Each working date must have an entry time");
        if (!endTimes.isEmpty() && endTimes.size() != labor.size())
            throw new IllegalArgumentException("Each working date must have an end time");
        for (int i = 0; i < endTimes.size(); i++)
            if (!endTimes.get(i).isAfter(timetable.get(i)))
                throw new IllegalArgumentException("La salida debe ser posterior a la entrada (mismo dia)");
    }

    public List<Instant> expectedEntries() {
        return IntStream.range(0, labor.size())
                .mapToObj(i -> labor.get(i).atTime(timetable.get(i)).atZone(zone).toInstant())
                .toList();
    }
}
