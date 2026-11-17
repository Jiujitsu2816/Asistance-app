package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Guardias en fechas concretas (hospital). Ejemplo: guardia de 24 h desde las 08:00
 * los dias 3, 7 y 11. Las guardias de presencia fisica se evaluan como un turno; las
 * localizadas no generan ausencias ni retrasos.
 */
public record OnCallSchedule(String id, String name, Duration tolerance, LocalTime start,
                             Duration duration, OnCallMode mode, SortedSet<LocalDate> dates)
        implements Schedule {
    public static final int MAX_DATES = 366;

    public OnCallSchedule {
        id = Require.text(id, "id", 64);
        name = Require.text(name, "name", 80);
        tolerance = Schedule.validTolerance(tolerance);
        Require.minutePrecision(start, "start");
        Require.present(duration, "durationMinutes");
        Require.present(mode, "mode");
        Require.present(dates, "dates");
        Require.that(duration.toMinutes() > 0 && duration.compareTo(Duration.ofHours(24)) <= 0,
                "La duracion de la guardia debe estar entre 1 minuto y 24 horas");
        Require.that(!dates.isEmpty() && dates.size() <= MAX_DATES,
                "Indica entre 1 y " + MAX_DATES + " fechas de guardia");
        Require.that(dates.stream().noneMatch(java.util.Objects::isNull), "Fecha de guardia no valida");
        // Misma hora de inicio y duracion <= 24 h: dos guardias consecutivas nunca se solapan.
        dates = java.util.Collections.unmodifiableSortedSet(new TreeSet<>(dates));
    }

    @Override public ScheduleKind kind() { return ScheduleKind.ON_CALL; }

    @Override public List<WorkPeriod> periodsStartingOn(LocalDate date) {
        if (!dates.contains(date)) return List.of();
        String label = (mode == OnCallMode.PHYSICAL ? "Guardia fisica " : "Guardia localizada ") + start;
        return List.of(new WorkPeriod(label, date, start, duration, mode == OnCallMode.PHYSICAL));
    }

    @Override public boolean exemptOnHolidays() { return false; }

    @Override public String summary() {
        return (mode == OnCallMode.PHYSICAL ? "Guardia fisica " : "Guardia localizada ")
                + duration.toHours() + " h desde " + start + " (" + dates.size() + " fechas)";
    }
}
