package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Horario corrido (oficina): un solo tramo, sin pausa, los dias laborables.
 * Ejemplo: lunes a viernes de 08:00 a 15:00.
 */
public record ContinuousSchedule(String id, String name, Duration tolerance,
                                 Set<DayOfWeek> workDays, TimeSlot slot) implements Schedule {
    public ContinuousSchedule {
        id = Require.text(id, "id", 64);
        name = Require.text(name, "name", 80);
        tolerance = Schedule.validTolerance(tolerance);
        workDays = WeekDays.valid(workDays);
        Require.present(slot, "slots");
    }

    @Override public ScheduleKind kind() { return ScheduleKind.CONTINUOUS; }

    @Override public List<WorkPeriod> periodsStartingOn(LocalDate date) {
        if (!workDays.contains(date.getDayOfWeek())) return List.of();
        return List.of(new WorkPeriod(slot.label(), date, slot.start(), slot.duration(), true));
    }

    @Override public boolean exemptOnHolidays() { return true; }

    @Override public String summary() {
        return "Corrido " + WeekDays.describe(workDays) + " " + slot.label();
    }
}
