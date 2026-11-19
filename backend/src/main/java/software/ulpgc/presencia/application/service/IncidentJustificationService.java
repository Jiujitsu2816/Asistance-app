package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.JustifyIncidentUseCase;
import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.JustificationRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.attendance.AttendanceEvaluator;
import software.ulpgc.presencia.domain.attendance.IncidentJustification;
import software.ulpgc.presencia.domain.attendance.IncidentKey;
import software.ulpgc.presencia.domain.error.IncidentAlreadyJustifiedException;
import software.ulpgc.presencia.domain.error.IncidentNotFoundException;
import software.ulpgc.presencia.domain.person.Person;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Las incidencias se calculan; para justificar una se recalcula su dia y se comprueba que
 * existe. La justificacion se guarda aparte y queda auditada.
 */
public final class IncidentJustificationService implements JustifyIncidentUseCase {
    private final AttendanceContext context;
    private final JustificationRepository justifications;
    private final AuditTrail audit;
    private final Transactions transactions;
    private final AttendanceEvaluator evaluator;
    private final Clock clock;

    public IncidentJustificationService(PersonRepository people, ScheduleRepository schedules,
                                        AssignmentRepository assignments, HolidayRepository holidays,
                                        ClockEventRepository events, JustificationRepository justifications,
                                        AuditTrail audit, Transactions transactions, Clock clock, ZoneId zone) {
        this.context = new AttendanceContext(people, schedules, assignments, holidays, events, justifications, zone);
        this.justifications = justifications;
        this.audit = audit;
        this.transactions = transactions;
        this.evaluator = new AttendanceEvaluator(zone);
        this.clock = clock;
    }

    @Override
    public IncidentJustification justify(JustifyIncidentCommand command) {
        String keyValue = Require.text(command.incidentKey(), "incidentKey", 160);
        String reason = Require.text(command.reason(), "reason", 200);
        String actor = Actors.normalize(command.actor());
        IncidentKey key = IncidentKey.parse(keyValue);
        return transactions.inTransaction(() -> {
            Person person = context.person(key.personId());
            boolean exists = evaluator.incidents(context.input(person, key.date(), key.date()), key.date(), key.date(),
                    clock.instant()).stream().anyMatch(i -> i.key().equals(keyValue));
            if (!exists) throw new IncidentNotFoundException("No existe la incidencia '" + keyValue + "'");
            if (justifications.findByKey(keyValue).isPresent())
                throw new IncidentAlreadyJustifiedException("La incidencia ya estaba justificada");
            IncidentJustification justification = new IncidentJustification(keyValue, person.id(), reason, actor,
                    clock.instant());
            justifications.save(justification);
            audit.record(actor, "INCIDENT_JUSTIFIED", "incident", keyValue, reason);
            return justification;
        });
    }
}
