package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turnos rotatorios (hospital): un ciclo de N dias que se repite a partir de
 * {@code cycleStart}. Ejemplo: Manana, Manana, Tarde, Tarde, Noche, Descanso, Descanso.
 * El dia del ciclo para una fecha es (fecha - cycleStart) mod N, tambien para fechas
 * anteriores a cycleStart.
 */
public record RotatingShiftSchedule(String id, String name, Duration tolerance,
                                    LocalDate cycleStart, List<CycleDay> cycle) implements Schedule {
    public static final int MAX_CYCLE_DAYS = 62;

    public RotatingShiftSchedule {
        id = Require.text(id, "id", 64);
        name = Require.text(name, "name", 80);
        tolerance = Schedule.validTolerance(tolerance);
        Require.present(cycleStart, "cycleStart");
        Require.present(cycle, "cycle");
        Require.that(!cycle.isEmpty() && cycle.size() <= MAX_CYCLE_DAYS,
                "El ciclo debe tener entre 1 y " + MAX_CYCLE_DAYS + " dias");
        Require.that(cycle.stream().noneMatch(java.util.Objects::isNull), "Dia de ciclo no valido");
        Require.that(cycle.stream().anyMatch(d -> d instanceof CycleDay.Work),
                "El ciclo debe contener al menos un dia de trabajo");
        cycle = List.copyOf(cycle);
        validateNoOverlap(cycle);
    }

    /** Un turno que cruza la medianoche no puede solaparse con el turno del dia siguiente. */
    private static void validateNoOverlap(List<CycleDay> cycle) {
        int n = cycle.size();
        for (int i = 0; i < n; i++) {
            if (!(cycle.get(i) instanceof CycleDay.Work(Shift current))) continue;
            if (!(cycle.get((i + 1) % n) instanceof CycleDay.Work(Shift next))) continue;
            long nextStartFromToday = 24 * 60L + next.start().toSecondOfDay() / 60L;
            Require.that(current.endMinuteOfStartDay() <= nextStartFromToday,
                    "El turno '" + current.label() + "' (dia " + (i + 1)
                            + ") se solapa con el turno del dia siguiente");
        }
    }

    public CycleDay dayOf(LocalDate date) {
        long offset = ChronoUnit.DAYS.between(cycleStart, date);
        return cycle.get((int) Math.floorMod(offset, cycle.size()));
    }

    @Override public ScheduleKind kind() { return ScheduleKind.ROTATING_SHIFT; }

    @Override public List<WorkPeriod> periodsStartingOn(LocalDate date) {
        if (dayOf(date) instanceof CycleDay.Work(Shift shift))
            return List.of(new WorkPeriod(shift.label(), date, shift.start(), shift.duration(), true));
        return List.of();
    }

    @Override public boolean exemptOnHolidays() { return false; }

    @Override public String summary() {
        return "Turnos (" + cycle.size() + " dias desde " + cycleStart + "): " + cycle.stream()
                .map(d -> d instanceof CycleDay.Work(Shift s) ? s.label() : "Descanso")
                .collect(Collectors.joining(", "));
    }
}
