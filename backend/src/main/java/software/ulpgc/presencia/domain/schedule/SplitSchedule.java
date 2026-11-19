package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Horario partido (tienda o restaurante): dos o mas tramos el mismo dia separados por
 * una pausa no trabajada. Ejemplo: martes a domingo 12:00-16:00 y 19:30-23:30.
 * Cada tramo se evalua por separado: se puede llegar tarde al segundo tramo.
 */
public record SplitSchedule(String id, String name, Duration tolerance,
                            Set<DayOfWeek> workDays, List<TimeSlot> slots) implements Schedule {
    public SplitSchedule {
        id = Require.text(id, "id", 64);
        name = Require.text(name, "name", 80);
        tolerance = Schedule.validTolerance(tolerance);
        workDays = WeekDays.valid(workDays);
        Require.present(slots, "slots");
        Require.that(slots.size() >= 2, "Un horario partido necesita al menos dos tramos");
        Require.that(slots.stream().noneMatch(java.util.Objects::isNull), "Tramo no valido");
        slots = slots.stream().sorted(Comparator.comparing(TimeSlot::start)).toList();
        for (int i = 1; i < slots.size(); i++)
            Require.that(slots.get(i).start().isAfter(slots.get(i - 1).end()),
                    "Los tramos de un horario partido deben estar separados por una pausa: "
                            + slots.get(i - 1).label() + " y " + slots.get(i).label());
    }

    @Override public ScheduleKind kind() { return ScheduleKind.SPLIT; }

    @Override public List<WorkPeriod> periodsStartingOn(LocalDate date) {
        if (!workDays.contains(date.getDayOfWeek())) return List.of();
        return slots.stream()
                .map(s -> new WorkPeriod(s.label(), date, s.start(), s.duration(), true))
                .toList();
    }

    @Override public boolean exemptOnHolidays() { return true; }

    @Override public String summary() {
        return "Partido " + WeekDays.describe(workDays) + " "
                + slots.stream().map(TimeSlot::label).collect(Collectors.joining(" y "));
    }
}
