package software.ulpgc.presencia.domain;

import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.schedule.ContinuousSchedule;
import software.ulpgc.presencia.domain.schedule.CycleDay;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.OnCallSchedule;
import software.ulpgc.presencia.domain.schedule.RotatingShiftSchedule;
import software.ulpgc.presencia.domain.schedule.Shift;
import software.ulpgc.presencia.domain.schedule.SplitSchedule;
import software.ulpgc.presencia.domain.schedule.TimeSlot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Datos de prueba compartidos. 2026-11-16 es lunes; Atlantic/Canary esta en UTC+0 en noviembre. */
public final class Fixtures {
    public static final ZoneId ZONE = ZoneId.of("Atlantic/Canary");
    public static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    public static final EnumSet<DayOfWeek> WEEKDAYS =
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private Fixtures() { }

    public static ContinuousSchedule office() {
        return new ContinuousSchedule("oficina", "Oficina", Duration.ofMinutes(10), WEEKDAYS,
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(15, 0)));
    }

    public static SplitSchedule restaurant() {
        return new SplitSchedule("restaurante", "Restaurante", Duration.ofMinutes(5),
                EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
                List.of(new TimeSlot(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                        new TimeSlot(LocalTime.of(19, 30), LocalTime.of(23, 30))));
    }

    /** Manana, Tarde, Noche (23:00 + 8 h), Descanso, Descanso; ciclo desde el lunes. */
    public static RotatingShiftSchedule hospitalShifts() {
        return new RotatingShiftSchedule("turnos", "Turnos hospital", Duration.ofMinutes(10), MONDAY,
                List.of(new CycleDay.Work(new Shift("Manana", LocalTime.of(7, 0), Duration.ofHours(8))),
                        new CycleDay.Work(new Shift("Tarde", LocalTime.of(15, 0), Duration.ofHours(8))),
                        new CycleDay.Work(new Shift("Noche", LocalTime.of(23, 0), Duration.ofHours(8))),
                        new CycleDay.Rest(), new CycleDay.Rest()));
    }

    public static OnCallSchedule onCall(OnCallMode mode, LocalDate... dates) {
        return new OnCallSchedule("guardia", "Guardia", Duration.ofMinutes(15), LocalTime.of(8, 0),
                Duration.ofHours(24), mode, new TreeSet<>(List.of(dates)));
    }

    public static Instant at(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ZONE).toInstant();
    }

    public static ClockEvent entry(String person, Instant at) {
        return event(person, ClockEventType.ENTRY, at);
    }

    public static ClockEvent exit(String person, Instant at) {
        return event(person, ClockEventType.EXIT, at);
    }

    private static ClockEvent event(String person, ClockEventType type, Instant at) {
        return new ClockEvent(UUID.randomUUID().toString(), person, type, at, at, ClockSource.KIOSK, null);
    }
}
