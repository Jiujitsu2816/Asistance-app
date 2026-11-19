package software.ulpgc.presencia.infrastructure.http;

import software.ulpgc.presencia.application.port.in.AccountAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.AccountAdministrationUseCase.CreateAccountCommand;
import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.application.port.in.AuthenticationUseCase;
import software.ulpgc.presencia.application.port.in.AuthenticationUseCase.LoginResult;
import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase;
import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase.JustifyIncidentCommand;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase.RegisterPersonCommand;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase.RegisterClockEventCommand;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase.AssignScheduleCommand;
import software.ulpgc.presencia.application.port.in.WorkplaceAdministrationUseCase;
import software.ulpgc.presencia.application.security.AccessControl;
import software.ulpgc.presencia.application.security.Principal;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.error.ForbiddenException;
import software.ulpgc.presencia.domain.person.Person;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.ScheduleKind;
import software.ulpgc.presencia.domain.workplace.Workplace;
import software.ulpgc.presencia.infrastructure.http.dto.AccountDto;
import software.ulpgc.presencia.infrastructure.http.dto.AccountRequest;
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
import software.ulpgc.presencia.infrastructure.http.dto.LoginRequest;
import software.ulpgc.presencia.infrastructure.http.dto.LoginResponse;
import software.ulpgc.presencia.infrastructure.http.dto.PasswordRequest;
import software.ulpgc.presencia.infrastructure.http.dto.PersonDto;
import software.ulpgc.presencia.infrastructure.http.dto.PresenceDto;
import software.ulpgc.presencia.infrastructure.http.dto.ReportDto;
import software.ulpgc.presencia.infrastructure.http.dto.Times;
import software.ulpgc.presencia.infrastructure.http.dto.UserDto;
import software.ulpgc.presencia.infrastructure.http.dto.WorkplaceDto;
import software.ulpgc.presencia.infrastructure.json.ScheduleDocument;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contrato HTTP del backend (prefijo /api). Cada ruta autentica la peticion, aplica los
 * permisos ({@link AccessControl}), traduce el JSON a comandos de los casos de uso y sus
 * resultados a DTO. No contiene reglas de negocio.
 *
 * <p>Permisos: las rutas de autenticacion y /health son publicas; un empleado solo puede
 * consultar y fichar sus propios datos; el resto de operaciones son del responsable.
 */
public final class ApiRoutes {
    /** Origenes que puede usar un empleado al fichar (ADMIN e IMPORT son de administracion). */
    private static final EnumSet<ClockSource> EMPLOYEE_SOURCES = EnumSet.of(ClockSource.KIOSK, ClockSource.WEB);

    private final AuthenticationUseCase auth;
    private final AccountAdministrationUseCase accounts;
    private final PersonAdministrationUseCase people;
    private final WorkplaceAdministrationUseCase workplaces;
    private final ScheduleAdministrationUseCase schedules;
    private final RegisterClockEventUseCase clocking;
    private final AttendanceQueryUseCase queries;
    private final JustifyIncidentUseCase justifications;
    private final Clock clock;
    private final ZoneId zone;
    private final Times times;

    public ApiRoutes(AuthenticationUseCase auth, AccountAdministrationUseCase accounts,
                     PersonAdministrationUseCase people, WorkplaceAdministrationUseCase workplaces,
                     ScheduleAdministrationUseCase schedules, RegisterClockEventUseCase clocking,
                     AttendanceQueryUseCase queries, JustifyIncidentUseCase justifications, Clock clock, ZoneId zone) {
        this.auth = auth;
        this.accounts = accounts;
        this.people = people;
        this.workplaces = workplaces;
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
                // ---- Publicas
                .get("/api/health", r -> ApiResponse.ok(health()))
                .get("/api/auth/status", r -> ApiResponse.ok(Map.of("setupRequired", auth.setupRequired())))
                .post("/api/auth/setup", r -> {
                    LoginRequest body = r.body(LoginRequest.class);
                    return ApiResponse.created(login(auth.setup(body.username(), chars(body.password()))));
                })
                .post("/api/auth/login", r -> {
                    LoginRequest body = r.body(LoginRequest.class);
                    return ApiResponse.ok(login(auth.login(body.username(), chars(body.password()))));
                })
                // ---- Sesion
                .post("/api/auth/logout", r -> {
                    auth.logout(r.bearerToken());
                    return new ApiResponse(204, null);
                })
                .get("/api/auth/me", r -> ApiResponse.ok(UserDto.from(user(r))))
                // ---- Cuentas (responsable)
                .get("/api/users", r -> {
                    admin(r);
                    return ApiResponse.ok(accounts.list().stream().map(a -> AccountDto.from(a, times)).toList());
                })
                .post("/api/users", r -> {
                    Principal admin = admin(r);
                    AccountRequest body = r.body(AccountRequest.class);
                    return ApiResponse.created(AccountDto.from(accounts.create(new CreateAccountCommand(
                            body.username(), chars(body.password()), body.role(), body.personId(), admin.username())), times));
                })
                .post("/api/users/{username}/password", r -> {
                    Principal admin = admin(r);
                    accounts.resetPassword(r.path("username"), chars(r.body(PasswordRequest.class).password()),
                            admin.username());
                    return new ApiResponse(204, null);
                })
                // ---- Personas y centros
                .get("/api/people", r -> {
                    admin(r);
                    return ApiResponse.ok(people.list().stream().map(PersonDto::from).toList());
                })
                .post("/api/people", r -> {
                    Principal admin = admin(r);
                    PersonDto body = r.body(PersonDto.class);
                    Person person = people.register(new RegisterPersonCommand(body.id(), body.displayName(),
                            body.team(), admin.username()));
                    return ApiResponse.created(PersonDto.from(person));
                })
                .get("/api/workplaces", r -> {
                    user(r);
                    return ApiResponse.ok(workplaces.list().stream().map(WorkplaceDto::from).toList());
                })
                .post("/api/workplaces", r -> {
                    Principal admin = admin(r);
                    WorkplaceDto body = r.body(WorkplaceDto.class);
                    return ApiResponse.created(WorkplaceDto.from(workplaces.create(
                            new Workplace(body.id(), body.name(), body.address()), admin.username())));
                })
                // ---- Horarios, asignaciones y festivos
                .get("/api/schedules", r -> {
                    user(r);
                    return ApiResponse.ok(schedules.listSchedules().stream().map(ScheduleDocument::from).toList());
                })
                .get("/api/schedules/{id}", r -> {
                    user(r);
                    return ApiResponse.ok(ScheduleDocument.from(schedules.getSchedule(r.path("id"))));
                })
                .post("/api/schedules", r -> {
                    Principal admin = admin(r);
                    ScheduleDocument body = r.body(ScheduleDocument.class);
                    return ApiResponse.created(ScheduleDocument.from(schedules.createSchedule(body::toDomain, admin.username())));
                })
                .get("/api/assignments", r -> {
                    String personId = AccessControl.personScope(user(r), r.query("personId"));
                    return ApiResponse.ok(schedules.listAssignments(personId).stream().map(AssignmentDto::from).toList());
                })
                .post("/api/assignments", r -> ApiResponse.created(AssignmentDto.from(assign(r, admin(r)))))
                .get("/api/holidays", r -> {
                    user(r);
                    return ApiResponse.ok(schedules.listHolidays().stream().map(HolidayDto::from).toList());
                })
                .post("/api/holidays", r -> {
                    Principal admin = admin(r);
                    HolidayDto body = r.body(HolidayDto.class);
                    return ApiResponse.created(HolidayDto.from(
                            schedules.addHoliday(new Holiday(body.date(), body.description()), admin.username())));
                })
                // ---- Fichajes
                .post("/api/clock-events", r -> ApiResponse.created(clockIn(r, user(r))))
                .get("/api/clock-events", r -> {
                    String personId = AccessControl.personScope(user(r), r.query("personId"));
                    LocalDate from = r.queryDate("from", today());
                    LocalDate to = r.queryDate("to", from);
                    return ApiResponse.ok(queries.clockEvents(personId, from, to).stream()
                            .map(e -> ClockEventDto.from(e, times)).toList());
                })
                // ---- Consultas
                .get("/api/presence", r -> {
                    admin(r);
                    Map<String, String> names = names();
                    return ApiResponse.ok(queries.currentPresence().stream()
                            .map(p -> PresenceDto.from(p, names.get(p.state().personId()), times)).toList());
                })
                .get("/api/presence/{personId}", r -> {
                    String personId = AccessControl.personScope(user(r), r.path("personId"));
                    return ApiResponse.ok(presence(personId));
                })
                .get("/api/reports/daily", r -> report(r, user(r)))
                .get("/api/incidents", r -> {
                    String personId = AccessControl.personScope(user(r), r.query("personId"));
                    LocalDate to = r.queryDate("to", today());
                    LocalDate from = r.queryDate("from", to.minusDays(6));
                    return ApiResponse.ok(queries.incidents(personId, from, to,
                                    r.queryEnum("type", IncidentType.class)).stream()
                            .map(i -> IncidentDto.from(i, times)).toList());
                })
                .post("/api/incidents/justifications", r -> {
                    Principal admin = admin(r);
                    JustificationRequest body = r.body(JustificationRequest.class);
                    return ApiResponse.created(JustificationDto.from(justifications.justify(
                            new JustifyIncidentCommand(body.incidentKey(), body.reason(), admin.username())), times));
                })
                .get("/api/audit", r -> {
                    admin(r);
                    return ApiResponse.ok(queries.audit(r.queryInt("limit", 50)).stream()
                            .map(a -> AuditDto.from(a, times)).toList());
                });
    }

    // ---- Autenticacion y permisos ---------------------------------------------------------

    private Principal user(ApiRequest r) {
        return auth.authenticate(r.bearerToken());
    }

    private Principal admin(ApiRequest r) {
        Principal principal = user(r);
        AccessControl.requireAdmin(principal);
        return principal;
    }

    private LoginResponse login(LoginResult result) {
        return new LoginResponse(result.token(), times.of(result.expiresAt()), UserDto.from(result.principal()));
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    // ---- Operaciones --------------------------------------------------------------------

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

    private ScheduleAssignment assign(ApiRequest r, Principal admin) throws Exception {
        AssignmentRequest body = r.body(AssignmentRequest.class);
        boolean hasPerson = body.personId() != null && !body.personId().isBlank();
        boolean hasTeam = body.team() != null && !body.team().isBlank();
        Require.that(hasPerson != hasTeam, "Indica 'personId' o 'team' (exactamente uno de los dos)");
        return schedules.assign(new AssignScheduleCommand(body.scheduleId(),
                hasPerson ? TargetType.PERSON : TargetType.TEAM, hasPerson ? body.personId() : body.team(),
                body.validFrom(), body.validTo(), body.workplaceId(), admin.username()));
    }

    /**
     * Un empleado solo ficha para si mismo, desde el terminal o la web y con la hora del
     * servidor. Las correcciones con hora indicada son del responsable.
     */
    private ClockEventResponse clockIn(ApiRequest r, Principal principal) throws Exception {
        ClockEventRequest body = r.body(ClockEventRequest.class);
        String personId = AccessControl.personScope(principal, body.personId());
        ClockSource source = body.source() == null && !principal.isAdmin() ? ClockSource.WEB : body.source();
        if (!principal.isAdmin()) {
            if (body.timestamp() != null)
                throw new ForbiddenException("Solo un responsable puede registrar fichajes con una hora indicada");
            if (source != null && !EMPLOYEE_SOURCES.contains(source))
                throw new ForbiddenException("Origen no permitido para un empleado: " + source);
        }
        ClockEvent event = clocking.register(new RegisterClockEventCommand(personId, body.type(), source,
                body.timestamp() == null ? null : body.timestamp().toInstant(), body.note(), principal.username()));
        return new ClockEventResponse(ClockEventDto.from(event, times), presence(event.personId()));
    }

    private PresenceDto presence(String personId) {
        return PresenceDto.from(queries.presenceOf(personId), names().get(personId), times);
    }

    private ApiResponse report(ApiRequest r, Principal principal) {
        String personId = AccessControl.personScope(principal, r.query("personId"));
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

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    private Map<String, String> names() {
        return people.list().stream().collect(Collectors.toMap(Person::id, Person::displayName));
    }
}
