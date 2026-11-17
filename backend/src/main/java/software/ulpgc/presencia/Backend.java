package software.ulpgc.presencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.service.AttendanceQueryService;
import software.ulpgc.presencia.application.service.IncidentJustificationService;
import software.ulpgc.presencia.application.service.PersonAdministrationService;
import software.ulpgc.presencia.application.service.RegisterClockEventService;
import software.ulpgc.presencia.application.service.ScheduleAdministrationService;
import software.ulpgc.presencia.infrastructure.config.AppConfig;
import software.ulpgc.presencia.infrastructure.http.ApiRoutes;
import software.ulpgc.presencia.infrastructure.http.HttpApiServer;
import software.ulpgc.presencia.infrastructure.http.Router;
import software.ulpgc.presencia.infrastructure.json.JsonMapper;
import software.ulpgc.presencia.infrastructure.persistence.Database;
import software.ulpgc.presencia.infrastructure.persistence.JdbcAssignmentRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcAuditTrail;
import software.ulpgc.presencia.infrastructure.persistence.JdbcClockEventRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcHolidayRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcJustificationRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcPersonRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcScheduleRepository;
import software.ulpgc.presencia.infrastructure.seed.DemoData;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Raiz de composicion: crea los adaptadores de salida (JDBC), los casos de uso y el
 * adaptador de entrada (HTTP). Es el unico sitio que conoce las implementaciones concretas.
 */
public final class Backend implements AutoCloseable {
    private final HttpApiServer server;
    private final Database db;

    public Backend(AppConfig config, Clock clock) throws IOException {
        ZoneId zone = config.zone();
        ObjectMapper json = JsonMapper.create();
        db = new Database(config.dbUrl(), config.dbUser(), config.dbPassword());
        db.migrate();

        var personRepository = new JdbcPersonRepository(db);
        var scheduleRepository = new JdbcScheduleRepository(db, json);
        var assignmentRepository = new JdbcAssignmentRepository(db);
        var holidayRepository = new JdbcHolidayRepository(db);
        var eventRepository = new JdbcClockEventRepository(db);
        var justificationRepository = new JdbcJustificationRepository(db);
        var audit = new JdbcAuditTrail(db, clock);

        PersonAdministrationUseCase people = new PersonAdministrationService(personRepository, audit, db);
        ScheduleAdministrationUseCase schedules = new ScheduleAdministrationService(scheduleRepository, assignmentRepository, personRepository,
                holidayRepository, audit, db);
        RegisterClockEventUseCase clocking = new RegisterClockEventService(personRepository, eventRepository, assignmentRepository, audit, db,
                clock, zone);
        AttendanceQueryUseCase queries = new AttendanceQueryService(personRepository, scheduleRepository, assignmentRepository,
                holidayRepository, eventRepository, justificationRepository, audit, clock, zone);
        JustifyIncidentUseCase justifications = new IncidentJustificationService(personRepository,
                scheduleRepository, assignmentRepository, holidayRepository, eventRepository,
                justificationRepository, audit, db, clock, zone);

        if (config.seed()) new DemoData(people, schedules, clocking, clock, zone).loadIfEmpty();

        Router routes = new ApiRoutes(people, schedules, clocking, queries, justifications, clock, zone).router();
        try {
            server = new HttpApiServer(config.host(), config.port(), routes, json, config.allowedOrigin());
        } catch (IOException e) {
            db.shutdown();
            throw new IOException("No se pudo abrir el puerto " + config.port() + ": " + e.getMessage(), e);
        }
    }

    public Backend start() {
        server.start();
        return this;
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        server.close();
        db.shutdown();
    }
}
