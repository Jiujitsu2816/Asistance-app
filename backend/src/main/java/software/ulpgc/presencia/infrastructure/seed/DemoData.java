package software.ulpgc.presencia.infrastructure.seed;

import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase.RegisterPersonCommand;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase.RegisterClockEventCommand;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase.AssignScheduleCommand;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.schedule.ContinuousSchedule;
import software.ulpgc.presencia.domain.schedule.CycleDay;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.OnCallSchedule;
import software.ulpgc.presencia.domain.schedule.RotatingShiftSchedule;
import software.ulpgc.presencia.domain.schedule.Schedule;
import software.ulpgc.presencia.domain.schedule.Shift;
import software.ulpgc.presencia.domain.schedule.SplitSchedule;
import software.ulpgc.presencia.domain.schedule.TimeSlot;
import software.ulpgc.presencia.domain.schedule.WorkPeriod;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;

/**
 * Datos de ejemplo ficticios (sin datos personales reales). Se cargan a traves de los casos
 * de uso, igual que lo haria un frontend, y solo si la base de datos esta vacia. Las fechas
 * son relativas al dia de arranque para que la demostracion funcione en cualquier fecha.
 */
public final class DemoData {
    private static final String ACTOR = "datos-iniciales";

    private final PersonAdministrationUseCase people;
    private final ScheduleAdministrationUseCase schedules;
    private final RegisterClockEventUseCase clocking;
    private final Clock clock;
    private final ZoneId zone;

    public DemoData(PersonAdministrationUseCase people, ScheduleAdministrationUseCase schedules,
                    RegisterClockEventUseCase clocking, Clock clock, ZoneId zone) {
        this.people = people;
        this.schedules = schedules;
        this.clocking = clocking;
        this.clock = clock;
        this.zone = zone;
    }

    public boolean loadIfEmpty() {
        if (!people.list().isEmpty() || !schedules.listSchedules().isEmpty()) return false;
        LocalDate today = LocalDate.now(clock.withZone(zone));

        person("EMP-001", "Persona Oficina A", "administracion");
        person("EMP-002", "Persona Oficina B", "administracion");
        person("EMP-003", "Persona Restaurante", "sala");
        person("EMP-004", "Persona Enfermeria", "planta-3");
        person("EMP-005", "Persona Medicina A", "urgencias");
        person("EMP-006", "Persona Medicina B", "urgencias");
        person("EMP-007", "Persona Sin Horario", null);
        person("EMP-008", "Persona Contrato Finalizado", null);

        Schedule office = schedules.createSchedule(id -> new ContinuousSchedule(id,
                "Oficina - jornada continua", Duration.ofMinutes(10), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                new TimeSlot(LocalTime.of(8, 0), LocalTime.of(15, 0))), ACTOR);
        Schedule restaurant = schedules.createSchedule(id -> new SplitSchedule(id,
                "Restaurante - horario partido", Duration.ofMinutes(5),
                EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY),
                List.of(new TimeSlot(LocalTime.of(12, 0), LocalTime.of(16, 0)),
                        new TimeSlot(LocalTime.of(19, 30), LocalTime.of(23, 30)))), ACTOR);
        LocalDate cycleStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Shift morning = new Shift("Manana", LocalTime.of(7, 0), Duration.ofHours(8));
        Shift afternoon = new Shift("Tarde", LocalTime.of(15, 0), Duration.ofHours(7));
        Shift night = new Shift("Noche", LocalTime.of(22, 0), Duration.ofHours(10));
        Schedule shifts = schedules.createSchedule(id -> new RotatingShiftSchedule(id,
                "Hospital - turnos rotatorios", Duration.ofMinutes(10), cycleStart,
                List.of(new CycleDay.Work(morning), new CycleDay.Work(morning), new CycleDay.Work(afternoon),
                        new CycleDay.Work(afternoon), new CycleDay.Work(night), new CycleDay.Rest(),
                        new CycleDay.Rest())), ACTOR);
        Schedule physicalGuard = schedules.createSchedule(id -> new OnCallSchedule(id,
                "Hospital - guardia de presencia fisica 24 h", Duration.ofMinutes(15), LocalTime.of(8, 0),
                Duration.ofHours(24), OnCallMode.PHYSICAL,
                new TreeSet<>(List.of(today.minusDays(6), today.minusDays(2), today.plusDays(2), today.plusDays(6)))),
                ACTOR);
        Schedule localizedGuard = schedules.createSchedule(id -> new OnCallSchedule(id,
                "Hospital - guardia localizada 17 h", Duration.ofMinutes(15), LocalTime.of(15, 0),
                Duration.ofHours(17), OnCallMode.LOCALIZED,
                new TreeSet<>(List.of(today.minusDays(3), today, today.plusDays(4)))), ACTOR);

        LocalDate from = today.minusDays(6);
        assign(office, TargetType.TEAM, "administracion", from);
        schedules.assign(new AssignScheduleCommand(office.id(), TargetType.PERSON, "EMP-008", today.minusDays(60),
                today.minusDays(31), ACTOR));
        assign(restaurant, TargetType.PERSON, "EMP-003", from);
        assign(shifts, TargetType.PERSON, "EMP-004", from);
        assign(physicalGuard, TargetType.PERSON, "EMP-005", from);
        assign(localizedGuard, TargetType.PERSON, "EMP-006", from);

        for (var holiday : List.of(new Holiday(LocalDate.of(2026, 12, 8), "Inmaculada Concepcion"),
                new Holiday(LocalDate.of(2026, 12, 25), "Navidad"),
                new Holiday(LocalDate.of(2027, 1, 1), "Ano Nuevo"),
                new Holiday(LocalDate.of(2027, 1, 6), "Epifania del Senor")))
            schedules.addHoliday(holiday, ACTOR);

        // Fichajes historicos de la ultima semana (origen IMPORT). En el ultimo dia laborable de
        // cada horario se introduce una anomalia para que las consultas muestren incidencias:
        // EMP-002 llega 27 min tarde, EMP-003 sale 45 min antes, EMP-004 no ficha la salida y
        // EMP-005 abandona la guardia 2 h antes.
        Instant now = clock.instant();
        history("EMP-001", office, today, now, new int[]{2, 3}, new int[]{2, 3});
        history("EMP-002", office, today, now, new int[]{4, 0}, new int[]{27, 0});
        history("EMP-003", restaurant, today, now, new int[]{0, 1}, new int[]{0, -45});
        history("EMP-004", shifts, today, now, new int[]{-5, 2}, null);
        history("EMP-005", physicalGuard, today, now, new int[]{-10, 5}, new int[]{-10, -120});
        return true;
    }

    private void person(String id, String name, String team) {
        people.register(new RegisterPersonCommand(id, name, team, ACTOR));
    }

    private void assign(Schedule schedule, TargetType type, String target, LocalDate from) {
        schedules.assign(new AssignScheduleCommand(schedule.id(), type, target, from, null, ACTOR));
    }

    /**
     * Registra los periodos ya terminados de los 6 dias anteriores con los desfases
     * {entrada, salida} en minutos; el ultimo periodo usa lastDay (null: sin fichaje de salida).
     */
    private void history(String personId, Schedule schedule, LocalDate today, Instant now, int[] usual,
                         int[] lastDay) {
        List<WorkPeriod> periods = today.minusDays(6).datesUntil(today)
                .flatMap(day -> schedule.periodsStartingOn(day).stream())
                .filter(p -> !p.startDateTime().atZone(zone).toInstant().plus(p.duration())
                        .plus(Duration.ofHours(1)).isAfter(now))
                .toList();
        if (periods.isEmpty()) return;
        for (WorkPeriod period : periods) {
            int[] offsets = period == periods.getLast() ? lastDay : usual;
            Instant start = period.startDateTime().atZone(zone).toInstant();
            Instant end = start.plus(period.duration());
            if (offsets == null) {
                clock(personId, ClockEventType.ENTRY, start.plusSeconds(60));
                return; // entrada sin salida: queda como salida pendiente
            }
            clock(personId, ClockEventType.ENTRY, start.plusSeconds(offsets[0] * 60L));
            clock(personId, ClockEventType.EXIT, end.plusSeconds(offsets[1] * 60L));
        }
    }

    private void clock(String personId, ClockEventType type, Instant at) {
        clocking.register(new RegisterClockEventCommand(personId, type, ClockSource.IMPORT, at,
                "Dato de ejemplo", ACTOR));
    }
}
