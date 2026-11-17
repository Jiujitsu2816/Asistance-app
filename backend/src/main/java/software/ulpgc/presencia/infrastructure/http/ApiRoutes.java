package software.ulpgc.presencia.infrastructure.http;

import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase;
import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase.JustifyIncidentCommand;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase.RegisterPersonCommand;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase.RegisterClockEventCommand;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase.AssignScheduleCommand;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.person.Person;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.ScheduleKind;
import software.ulpgc.presencia.infrastructure.http.dto.AssignmentDto;
import software.ulpgc.presencia.infrastructure.http.dto.AssignmentRequest;
import software.ulpgc.presencia.infrastructure.http.dto.AuditDto;
import software.ulpgc.presencia.infrastructure.http.dto.ClockEventDto;
import software.ulpgc.presencia.infrastructure.http.dto.ClockEventRequest;
import software.ulpgc.presencia.infrastructure.http.dto.ClockEventResponse;
import software.ulpgc.presencia.infrastructure.http.dto.DailyAttendanceDto;
import software.ulpgc.presencia.infrastructure.http.dto.HolidayDto;
import software.ulpgc.presencia.infrastructure.http.dto.IncidentDto;
import software.ulpgc.presencia.infrastructure.http.dto.JustificationDto;
import software.ulpgc.presencia.infrastructure.http.dto.JustificationRequest;
import software.ulpgc.presencia.infrastructure.http.dto.PersonDto;
import software.ulpgc.presencia.infrastructure.http.dto.PresenceDto;
import software.ulpgc.presencia.infrastructure.http.dto.ReportDto;
import software.ulpgc.presencia.infrastructure.http.dto.Times;
import software.ulpgc.presencia.infrastructure.json.ScheduleDocument;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contrato HTTP del backend (prefijo /api). Cada ruta solo traduce JSON a comandos de los
 * casos de uso y sus resultados a DTO; no contiene reglas de negocio.
 */
public final class ApiRoutes {
    private final PersonAdministrationUseCase people;
    private final ScheduleAdministrationUseCase schedules;
    private final RegisterClockEventUseCase clocking;
    private final AttendanceQueryUseCase queries;
    private final JustifyIncidentUseCase justifications;
    private final Clock clock;
    private final ZoneId zone;
    private final Times times;

    public ApiRoutes(PersonAdministrationUseCase people, ScheduleAdministrationUseCase schedules,
                     RegisterClockEventUseCase clocking, AttendanceQueryUseCase queries,
                     JustifyIncidentUseCase justifications, Clock clock, ZoneId zone) {
        this.people = people;
        this.schedules = schedules;
        this.clocking = clocking;
        this.queries = queries;
        this.justifications = justifications;
        this.clock = clock;
        this.zone = zone;
        this.times = new Times(zone);
    }

    public Router router() {
        return new Router()
                .get("/api/health", r -> ApiResponse.ok(health()))
                // Personas
                .get("/api/people", r -> ApiResponse.ok(people.list().stream().map(PersonDto::from).toList()))
                .post("/api/people", r -> {
                    PersonDto body = r.body(PersonDto.class);
                    Person person = people.register(new RegisterPersonCommand(body.id(), body.displayName(),
                            body.team(), r.actor()));
                    return ApiResponse.created(PersonDto.from(person));
                })
                // Horarios, asignaciones y festivos
                .get("/api/schedules", r -> ApiResponse.ok(schedules.listSchedules().stream()
                        .map(ScheduleDocument::from).toList()))
                .get("/api/schedules/{id}", r -> ApiResponse.ok(ScheduleDocument.from(schedules.getSchedule(r.path("id")))))
                .post("/api/schedules", r -> {
                    ScheduleDocument body = r.body(ScheduleDocument.class);
                    return ApiResponse.created(ScheduleDocument.from(schedules.createSchedule(body::toDomain, r.actor())));
                })
                .get("/api/assignments", r -> ApiResponse.ok(schedules.listAssignments(r.query("personId")).stream()
                        .map(AssignmentDto::from).toList()))
                .post("/api/assignments", r -> ApiResponse.created(AssignmentDto.from(assign(r))))
                .get("/api/holidays", r -> ApiResponse.ok(schedules.listHolidays().stream().map(HolidayDto::from).toList()))
                .post("/api/holidays", r -> {
                    HolidayDto body = r.body(HolidayDto.class);
                    return ApiResponse.created(HolidayDto.from(
                            schedules.addHoliday(new Holiday(body.date(), body.description()), r.actor())));
                })
                // Fichajes
                .post("/api/clock-events", r -> ApiResponse.created(clock(r)))
                .get("/api/clock-events", r -> {
                    LocalDate today = LocalDate.now(clock.withZone(zone));
                    LocalDate from = r.queryDate("from", today);
                    LocalDate to = r.queryDate("to", from);
                    return ApiResponse.ok(queries.clockEvents(r.query("personId"), from, to).stream()
                            .map(e -> ClockEventDto.from(e, times)).toList());
                })
                // Consultas de presencia
                .get("/api/presence", r -> {
                    Map<String, String> names = names();
                    return ApiResponse.ok(queries.currentPresence().stream()
                            .map(p -> PresenceDto.from(p, names.get(p.personId()), times)).toList());
                })
                .get("/api/presence/{personId}", r -> ApiResponse.ok(presence(r.path("personId"))))
                .get("/api/reports/daily", this::report)
                .get("/api/incidents", r -> {
                    LocalDate to = r.queryDate("to", LocalDate.now(clock.withZone(zone)));
                    LocalDate from = r.queryDate("from", to.minusDays(6));
                    return ApiResponse.ok(queries.incidents(r.query("personId"), from, to,
                                    r.queryEnum("type", IncidentType.class)).stream()
                            .map(i -> IncidentDto.from(i, times)).toList());
                })
                .post("/api/incidents/justifications", r -> {
                    JustificationRequest body = r.body(JustificationRequest.class);
                    return ApiResponse.created(JustificationDto.from(justifications.justify(
                            new JustifyIncidentCommand(body.incidentKey(), body.reason(), r.actor())), times));
                })
                .get("/api/audit", r -> ApiResponse.ok(queries.audit(r.queryInt("limit", 50)).stream()
                        .map(a -> AuditDto.from(a, times)).toList()));
    }

    private Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("zone", zone.getId());
        body.put("serverTime", times.of(clock.instant()));
        body.put("scheduleKinds", ScheduleKind.values());
        body.put("onCallModes", OnCallMode.values());
        body.put("clockSources", ClockSource.values());
        body.put("incidentTypes", IncidentType.values());
        return body;
    }

    private software.ulpgc.presencia.domain.assignment.ScheduleAssignment assign(ApiRequest r) throws Exception {
        AssignmentRequest body = r.body(AssignmentRequest.class);
        boolean hasPerson = body.personId() != null && !body.personId().isBlank();
        boolean hasTeam = body.team() != null && !body.team().isBlank();
        Require.that(hasPerson != hasTeam, "Indica 'personId' o 'team' (exactamente uno de los dos)");
        return schedules.assign(new AssignScheduleCommand(body.scheduleId(),
                hasPerson ? TargetType.PERSON : TargetType.TEAM, hasPerson ? body.personId() : body.team(),
                body.validFrom(), body.validTo(), r.actor()));
    }

    private ClockEventResponse clock(ApiRequest r) throws Exception {
        ClockEventRequest body = r.body(ClockEventRequest.class);
        ClockEvent event = clocking.register(new RegisterClockEventCommand(body.personId(), body.type(),
                body.source(), body.timestamp() == null ? null : body.timestamp().toInstant(), body.note(),
                r.actor()));
        return new ClockEventResponse(ClockEventDto.from(event, times), presence(event.personId()));
    }

    private PresenceDto presence(String personId) {
        return PresenceDto.from(queries.presenceOf(personId), names().get(personId), times);
    }

    private ApiResponse report(ApiRequest r) {
        String personId = r.query("personId");
        Require.that(personId != null, "Falta el parametro 'personId'");
        LocalDate from = r.queryDate("from");
        LocalDate to = r.queryDate("to", from);
        List<DailyAttendance> days = queries.dailyReport(personId, from, to);
        long expected = days.stream().mapToLong(DailyAttendance::expectedMinutes).sum();
        long worked = days.stream().mapToLong(DailyAttendance::workedMinutes).sum();
        long deviation = days.stream().map(DailyAttendance::deviationMinutes).filter(Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        int incidents = days.stream().mapToInt(d -> d.incidents().size()).sum();
        return ApiResponse.ok(new ReportDto(personId, names().get(personId), from, to, expected, worked, deviation,
                incidents, days.stream().map(d -> DailyAttendanceDto.from(d, times)).toList()));
    }

    private Map<String, String> names() {
        return people.list().stream().collect(Collectors.toMap(Person::id, Person::displayName));
    }
}
