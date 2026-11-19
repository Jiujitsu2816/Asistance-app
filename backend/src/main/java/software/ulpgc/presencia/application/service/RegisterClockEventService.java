package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignments;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.clocking.ClockingPolicy;
import software.ulpgc.presencia.domain.error.DateOutOfPeriodException;
import software.ulpgc.presencia.domain.error.PersonNotFoundException;
import software.ulpgc.presencia.domain.error.ScheduleNotAssignedException;
import software.ulpgc.presencia.domain.person.Person;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caso de uso de fichaje. Las comprobaciones y el guardado de una misma persona se
 * serializan con un cerrojo por persona para que dos peticiones simultaneas no puedan
 * registrar dos entradas seguidas.
 */
public final class RegisterClockEventService implements RegisterClockEventUseCase {
    private final PersonRepository people;
    private final ClockEventRepository events;
    private final AssignmentRepository assignments;
    private final AuditTrail audit;
    private final Transactions transactions;
    private final Clock clock;
    private final ZoneId zone;
    private final ClockingPolicy policy;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public RegisterClockEventService(PersonRepository people, ClockEventRepository events,
                                     AssignmentRepository assignments, AuditTrail audit, Transactions transactions,
                                     Clock clock, ZoneId zone) {
        this.people = people;
        this.events = events;
        this.assignments = assignments;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.zone = zone;
        this.policy = new ClockingPolicy(zone);
    }

    @Override
    public ClockEvent register(RegisterClockEventCommand command) {
        String personId = Require.text(command.personId(), "personId", 32);
        ClockEventType type = Require.present(command.type(), "type");
        ClockSource source = Require.present(command.source(), "source");
        Person person = people.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException("No existe ninguna persona con id '" + personId + "'"));
        // Los fichajes se guardan con precision de segundos.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant at = command.timestamp() == null ? now : command.timestamp().truncatedTo(ChronoUnit.SECONDS);

        synchronized (locks.computeIfAbsent(person.id(), id -> new Object())) {
            return transactions.inTransaction(() -> {
                var last = events.findLast(person.id());
                policy.check(last, type, at, now);
                // La entrada toma el centro de la asignacion vigente; la salida, el de su entrada.
                String workplace = type == ClockEventType.ENTRY
                        ? requireAssignment(person, LocalDate.ofInstant(at, zone)).workplaceId()
                        : last.map(ClockEvent::workplaceId).orElse(null);
                ClockEvent event = new ClockEvent(UUID.randomUUID().toString(), person.id(), type, at, now, source,
                        command.note(), workplace);
                events.append(event);
                audit.record(Actors.normalize(command.actor()), "CLOCK_" + type, "clock_event", event.id(),
                        person.id() + " " + type + " " + at + " origen " + source
                                + (command.timestamp() == null ? "" : " (hora indicada manualmente)"));
                return event;
            });
        }
    }

    /** Una entrada exige un horario vigente en esa fecha. La salida cierra la entrada abierta. */
    private ScheduleAssignment requireAssignment(Person person, LocalDate date) {
        ScheduleAssignments resolved = new ScheduleAssignments(person, assignments.findAll());
        if (resolved.isEmpty())
            throw new ScheduleNotAssignedException("La persona '" + person.id() + "' no tiene ningun horario asignado");
        return resolved.on(date).orElseThrow(() -> new DateOutOfPeriodException("El " + date
                + " esta fuera de los periodos de horario asignados a '" + person.id() + "'"));
    }
}
