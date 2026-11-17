package software.ulpgc.presencia.domain.attendance;

import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.calendar.HolidayCalendar;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.WorkSession;
import software.ulpgc.presencia.domain.schedule.Schedule;
import software.ulpgc.presencia.domain.schedule.WorkPeriod;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de dominio que evalua los fichajes de una persona contra su horario.
 *
 * <p>Reglas de computo:
 * <ol>
 *   <li>Los fichajes se emparejan en sesiones (entrada-salida).</li>
 *   <li>Cada sesion se asocia al periodo esperado con el que mas se solapa (tambien
 *       periodos del dia anterior o siguiente, para turnos de noche y guardias). Si no se
 *       solapa con ninguno es trabajo fuera de horario.</li>
 *   <li>Sobre cada periodo ya iniciado se detectan: ausencia, retraso, salida anticipada y
 *       salida pendiente, aplicando la tolerancia del horario.</li>
 *   <li>Las horas trabajadas solo cuentan sesiones cerradas. Una entrada abierta no suma
 *       horas hasta que se registra la salida.</li>
 *   <li>La desviacion (trabajado - esperado) solo se calcula cuando el dia ha terminado.</li>
 * </ol>
 */
public final class AttendanceEvaluator {
    /** Una entrada fuera de horario abierta durante mas tiempo se considera salida pendiente. */
    public static final Duration MAX_UNSCHEDULED_OPEN = Duration.ofHours(16);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId zone;

    public AttendanceEvaluator(ZoneId zone) {
        this.zone = zone;
    }

    /** Datos necesarios para evaluar a una persona. */
    public record Input(String personId, ScheduleLookup schedules, HolidayCalendar holidays,
                        List<ClockEvent> events, Map<String, IncidentJustification> justifications) {
        public Input {
            events = events.stream()
                    .sorted(Comparator.comparing(ClockEvent::occurredAt).thenComparing(ClockEvent::recordedAt))
                    .toList();
            justifications = Map.copyOf(justifications);
        }
    }

    /** Fechas cuyos fichajes hay que cargar para evaluar el rango [from, to]. */
    public static LocalDate eventsFrom(LocalDate from) {
        return from.minusDays(2);
    }

    public static LocalDate eventsToExclusive(LocalDate to) {
        return to.plusDays(3);
    }

    public List<DailyAttendance> evaluate(Input input, LocalDate from, LocalDate to, Instant now) {
        Evaluation evaluation = new Evaluation(input, from, to, now);
        List<DailyAttendance> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1))
            days.add(evaluation.day(date));
        return days;
    }

    public List<Incident> incidents(Input input, LocalDate from, LocalDate to, Instant now) {
        return evaluate(input, from, to, now).stream().flatMap(d -> d.incidents().stream()).toList();
    }

    public PresenceState presence(Input input, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        Evaluation evaluation = new Evaluation(input, today.minusDays(1), today, now);
        DailyAttendance todayResult = evaluation.day(today);
        Optional<ClockEvent> last = input.events().isEmpty() ? Optional.empty()
                : Optional.of(input.events().getLast());
        boolean inside = last.isPresent() && last.get().type() == ClockEventType.ENTRY;
        Instant since = last.map(ClockEvent::occurredAt).orElse(null);

        Planned current = evaluation.currentPeriod(today);
        PresenceStatus status;
        if (inside) {
            Planned assigned = evaluation.periodOfOpenSession();
            if (current != null) status = PresenceStatus.PRESENT;
            else if (assigned != null)
                status = now.isAfter(assigned.end().plus(assigned.tolerance()))
                        ? PresenceStatus.CHECKOUT_PENDING : PresenceStatus.PRESENT;
            else status = Duration.between(since, now).compareTo(MAX_UNSCHEDULED_OPEN) > 0
                        ? PresenceStatus.CHECKOUT_PENDING : PresenceStatus.PRESENT_OUT_OF_SCHEDULE;
        } else if (current != null) {
            if (!current.required()) status = PresenceStatus.ON_CALL;
            else if (now.isBefore(current.start().plus(current.tolerance()))) status = PresenceStatus.EXPECTED;
            else status = PresenceStatus.ABSENT;
        } else if (evaluation.plan(today).schedule().isEmpty()) {
            status = PresenceStatus.NO_SCHEDULE;
        } else {
            status = PresenceStatus.OFF_DUTY;
        }
        return new PresenceState(input.personId(), status, inside, since,
                current == null ? null : current.period().label(),
                current == null ? null : current.start(),
                current == null ? null : current.end(),
                todayResult.status());
    }

    // ------------------------------------------------------------------------------------

    private record Planned(Schedule schedule, WorkPeriod period, Instant start, Instant end) {
        Duration tolerance() { return schedule.tolerance(); }

        boolean required() { return period.presenceRequired(); }

        LocalDate date() { return period.date(); }
    }

    private record Plan(LocalDate date, Optional<Schedule> schedule, Optional<Holiday> holiday,
                        boolean skippedByHoliday, List<Planned> periods) { }

    /** Calculo para un rango de fechas; agrupa la planificacion y la asignacion de sesiones. */
    private final class Evaluation {
        private final Input input;
        private final Instant now;
        private final Map<LocalDate, Plan> plans = new LinkedHashMap<>();
        private final Map<Planned, List<WorkSession>> byPeriod = new IdentityHashMap<>();
        private final List<WorkSession> unscheduled = new ArrayList<>();
        private WorkSession openSession;
        private Planned openSessionPeriod;

        Evaluation(Input input, LocalDate from, LocalDate to, Instant now) {
            this.input = input;
            this.now = now;
            for (LocalDate d = from.minusDays(1); !d.isAfter(to.plusDays(1)); d = d.plusDays(1))
                plans.put(d, buildPlan(d));
            Instant windowStart = from.minusDays(1).atStartOfDay(zone).toInstant();
            Instant windowEnd = to.plusDays(2).atStartOfDay(zone).toInstant();
            List<Planned> all = plans.values().stream().flatMap(p -> p.periods().stream()).toList();
            for (WorkSession session : WorkSession.pair(input.events())) {
                if (session.start().isBefore(windowStart) || !session.start().isBefore(windowEnd)) continue;
                Planned best = bestPeriod(session, all);
                if (best == null) unscheduled.add(session);
                else byPeriod.computeIfAbsent(best, k -> new ArrayList<>()).add(session);
                if (session.isOpen()) {
                    openSession = session;
                    openSessionPeriod = best;
                }
            }
        }

        Plan plan(LocalDate date) {
            return plans.get(date);
        }

        private Plan buildPlan(LocalDate date) {
            Optional<Schedule> schedule = input.schedules().on(date);
            Optional<Holiday> holiday = input.holidays().on(date);
            if (schedule.isEmpty()) return new Plan(date, schedule, holiday, false, List.of());
            boolean skipped = holiday.isPresent() && schedule.get().exemptOnHolidays();
            List<Planned> periods = skipped ? List.of() : schedule.get().periodsStartingOn(date).stream()
                    .map(p -> {
                        Instant start = p.startDateTime().atZone(zone).toInstant();
                        return new Planned(schedule.get(), p, start, start.plus(p.duration()));
                    }).toList();
            return new Plan(date, schedule, holiday, skipped, periods);
        }

        private Planned bestPeriod(WorkSession session, List<Planned> candidates) {
            Instant end = session.end().orElse(now);
            Planned best = null;
            long bestOverlap = 0;
            for (Planned p : candidates) {
                Instant from = max(session.start(), p.start());
                Instant to = min(end, p.end());
                long overlap = Duration.between(from, to).toSeconds();
                if (overlap > bestOverlap) {
                    best = p;
                    bestOverlap = overlap;
                }
            }
            return best;
        }

        Planned currentPeriod(LocalDate today) {
            for (LocalDate d : List.of(today.minusDays(1), today))
                for (Planned p : plans.get(d).periods())
                    if (!now.isBefore(p.start()) && now.isBefore(p.end())) return p;
            return null;
        }

        Planned periodOfOpenSession() {
            return openSession == null ? null : openSessionPeriod;
        }

        DailyAttendance day(LocalDate date) {
            Plan plan = plans.get(date);
            List<Incident> incidents = new ArrayList<>();
            List<PeriodResult> results = new ArrayList<>();
            long expected = 0;
            long worked = 0;
            boolean open = false;
            boolean allEnded = true;
            boolean anyStarted = false;

            for (Planned p : plan.periods()) {
                List<WorkSession> sessions = byPeriod.getOrDefault(p, List.of()).stream()
                        .sorted(Comparator.comparing(WorkSession::start)).toList();
                long periodWorked = sessions.stream().mapToLong(s -> s.worked().toMinutes()).sum();
                boolean periodOpen = sessions.stream().anyMatch(WorkSession::isOpen);
                Instant firstEntry = sessions.isEmpty() ? null : sessions.getFirst().start();
                Instant lastExit = sessions.isEmpty() ? null : sessions.getLast().end().orElse(null);
                results.add(new PeriodResult(p.period().label(), p.start(), p.end(), p.required(),
                        firstEntry, lastExit, periodWorked, periodOpen));
                if (p.required()) expected += p.period().duration().toMinutes();
                worked += periodWorked;
                open |= periodOpen;
                allEnded &= !now.isBefore(p.end());
                anyStarted |= !now.isBefore(p.start());
                incidents.addAll(periodIncidents(p, sessions));
            }
            for (WorkSession s : unscheduled) {
                if (!LocalDate.ofInstant(s.start(), zone).equals(date)) continue;
                worked += s.worked().toMinutes();
                open |= s.isOpen();
                incidents.add(incident(date, IncidentType.UNSCHEDULED_WORK, "Fuera de horario", s.start(), null,
                        s.start(), s.worked().toMinutes(), "Fichaje fuera de los periodos esperados"));
                if (s.isOpen() && Duration.between(s.start(), now).compareTo(MAX_UNSCHEDULED_OPEN) > 0)
                    incidents.add(incident(date, IncidentType.MISSING_CHECKOUT, "Fuera de horario", s.start(),
                            null, null, Duration.between(s.start(), now).toMinutes(),
                            "Entrada fuera de horario sin salida desde hace mas de "
                                    + MAX_UNSCHEDULED_OPEN.toHours() + " h"));
            }

            Long deviation = plan.schedule().isPresent() && allEnded && !open ? worked - expected : null;
            DayStatus status = status(plan, incidents, open, allEnded, anyStarted);
            return new DailyAttendance(input.personId(), date,
                    plan.schedule().map(Schedule::id).orElse(null),
                    plan.schedule().map(Schedule::name).orElse(null),
                    plan.holiday().map(Holiday::description).orElse(null),
                    status, expected, worked, deviation, open, List.copyOf(results), List.copyOf(incidents));
        }

        private List<Incident> periodIncidents(Planned p, List<WorkSession> sessions) {
            List<Incident> found = new ArrayList<>();
            if (now.isBefore(p.start())) return found;
            String label = p.period().label();
            Duration tolerance = p.tolerance();
            if (sessions.isEmpty()) {
                if (p.required() && !now.isBefore(p.start().plus(tolerance)))
                    found.add(incident(p.date(), IncidentType.ABSENCE, label, p.start(), p.start(), null,
                            p.period().duration().toMinutes(), "Sin fichaje de entrada en el periodo " + label));
                return found;
            }
            Instant firstEntry = sessions.getFirst().start();
            if (p.required() && firstEntry.isAfter(p.start().plus(tolerance))) {
                long late = Duration.between(p.start(), firstEntry).toMinutes();
                found.add(incident(p.date(), IncidentType.LATE_ARRIVAL, label, p.start(), p.start(), firstEntry,
                        late, "Entrada a las " + hour(firstEntry) + ", " + late + " min tarde"));
            }
            WorkSession last = sessions.getLast();
            if (last.isOpen()) {
                if (now.isAfter(p.end().plus(tolerance)))
                    found.add(incident(p.date(), IncidentType.MISSING_CHECKOUT, label, p.end(), p.end(), null,
                            Duration.between(p.end(), now).toMinutes(),
                            "Sin salida registrada; el periodo termino a las " + hour(p.end())));
            } else if (p.required() && !now.isBefore(p.end())) {
                Instant exit = last.end().orElseThrow();
                if (exit.isBefore(p.end().minus(tolerance))) {
                    long early = Duration.between(exit, p.end()).toMinutes();
                    found.add(incident(p.date(), IncidentType.EARLY_DEPARTURE, label, p.end(), p.end(), exit,
                            early, "Salida a las " + hour(exit) + ", " + early + " min antes"));
                }
            }
            return found;
        }

        /**
         * @param reference instante que identifica la incidencia en su clave (inicio o fin del
         *                  periodo, o la entrada si es fuera de horario).
         */
        private Incident incident(LocalDate date, IncidentType type, String label, Instant reference,
                                  Instant expectedAt, Instant actualAt, long minutes, String description) {
            LocalTime refTime = reference.atZone(zone).toLocalTime().withSecond(0).withNano(0);
            String key = new IncidentKey(input.personId(), date, type, refTime).value();
            return new Incident(key, input.personId(), date, type, label, expectedAt, actualAt, minutes,
                    description, input.justifications().get(key));
        }

        private DayStatus status(Plan plan, List<Incident> incidents, boolean open, boolean allEnded,
                                 boolean anyStarted) {
            if (plan.schedule().isEmpty()) return DayStatus.NO_SCHEDULE;
            List<IncidentType> pending = incidents.stream().filter(i -> !i.justified()).map(Incident::type).toList();
            if (pending.contains(IncidentType.ABSENCE)) return DayStatus.ABSENT;
            if (pending.contains(IncidentType.MISSING_CHECKOUT)) return DayStatus.CHECKOUT_PENDING;
            if (pending.contains(IncidentType.LATE_ARRIVAL)) return DayStatus.LATE;
            if (pending.contains(IncidentType.EARLY_DEPARTURE)) return DayStatus.EARLY_DEPARTURE;
            boolean justified = incidents.stream().anyMatch(Incident::justified);
            if (plan.periods().isEmpty()) {
                if (pending.contains(IncidentType.UNSCHEDULED_WORK)) return DayStatus.EXTRA_WORK;
                if (plan.skippedByHoliday()) return DayStatus.HOLIDAY;
                return justified ? DayStatus.JUSTIFIED : DayStatus.REST;
            }
            if (open || (anyStarted && !allEnded)) return DayStatus.IN_PROGRESS;
            if (!anyStarted) return DayStatus.PENDING;
            if (justified) return DayStatus.JUSTIFIED;
            if (pending.contains(IncidentType.UNSCHEDULED_WORK)) return DayStatus.EXTRA_WORK;
            return DayStatus.COMPLETED;
        }
    }

    private String hour(Instant instant) {
        return HOUR.format(instant.atZone(zone));
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
