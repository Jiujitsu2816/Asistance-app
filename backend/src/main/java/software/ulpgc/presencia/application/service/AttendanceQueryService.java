package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.JustificationRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.attendance.AttendanceEvaluator;
import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.Incident;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.attendance.PresenceState;
import software.ulpgc.presencia.domain.audit.AuditEntry;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.person.Person;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class AttendanceQueryService implements AttendanceQueryUseCase {
    private final AttendanceContext context;
    private final ClockEventRepository events;
    private final AuditTrail audit;
    private final AttendanceEvaluator evaluator;
    private final Clock clock;
    private final ZoneId zone;

    public AttendanceQueryService(PersonRepository people, ScheduleRepository schedules,
                                  AssignmentRepository assignments, HolidayRepository holidays,
                                  ClockEventRepository events, JustificationRepository justifications,
                                  AuditTrail audit, Clock clock, ZoneId zone) {
        this.context = new AttendanceContext(people, schedules, assignments, holidays, events, justifications, zone);
        this.events = events;
        this.audit = audit;
        this.evaluator = new AttendanceEvaluator(zone);
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public List<PresenceState> currentPresence() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        return context.allPeople().stream()
                .sorted(java.util.Comparator.comparing(Person::id))
                .map(p -> evaluator.presence(context.input(p, today.minusDays(1), today.plusDays(1)), now))
                .toList();
    }

    @Override
    public PresenceState presenceOf(String personId) {
        Person person = context.person(personId);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        return evaluator.presence(context.input(person, today.minusDays(1), today.plusDays(1)), now);
    }

    @Override
    public List<ClockEvent> clockEvents(String personId, LocalDate from, LocalDate to) {
        DateRange.validate(from, to);
        String id = personId == null || personId.isBlank() ? null : context.person(personId).id();
        return events.find(id, context.startOf(from), context.startOf(to.plusDays(1)));
    }

    @Override
    public List<DailyAttendance> dailyReport(String personId, LocalDate from, LocalDate to) {
        DateRange.validate(from, to);
        Person person = context.person(personId);
        return evaluator.evaluate(context.input(person, from, to), from, to, clock.instant());
    }

    @Override
    public List<Incident> incidents(String personId, LocalDate from, LocalDate to, IncidentType type) {
        DateRange.validate(from, to);
        List<Person> people = personId == null || personId.isBlank()
                ? context.allPeople() : List.of(context.person(personId));
        Instant now = clock.instant();
        return people.stream()
                .flatMap(p -> evaluator.incidents(context.input(p, from, to), from, to, now).stream())
                .filter(i -> type == null || i.type() == type)
                .sorted(java.util.Comparator.comparing(Incident::date).thenComparing(Incident::personId))
                .toList();
    }

    @Override
    public List<AuditEntry> audit(int limit) {
        Require.that(limit > 0 && limit <= 500, "El limite debe estar entre 1 y 500");
        return audit.recent(limit);
    }
}
