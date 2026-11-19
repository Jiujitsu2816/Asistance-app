package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.application.port.out.WorkplaceRepository;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.error.AssignmentOverlapException;
import software.ulpgc.presencia.domain.error.HolidayAlreadyExistsException;
import software.ulpgc.presencia.domain.error.InvalidDataException;
import software.ulpgc.presencia.domain.error.PersonNotFoundException;
import software.ulpgc.presencia.domain.error.ScheduleNotFoundException;
import software.ulpgc.presencia.domain.error.WorkplaceNotFoundException;
import software.ulpgc.presencia.domain.person.Person;
import software.ulpgc.presencia.domain.schedule.Schedule;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class ScheduleAdministrationService implements ScheduleAdministrationUseCase {
    private final ScheduleRepository schedules;
    private final AssignmentRepository assignments;
    private final PersonRepository people;
    private final HolidayRepository holidays;
    private final WorkplaceRepository workplaces;
    private final AuditTrail audit;
    private final Transactions transactions;

    public ScheduleAdministrationService(ScheduleRepository schedules, AssignmentRepository assignments,
                                         PersonRepository people, HolidayRepository holidays,
                                         WorkplaceRepository workplaces, AuditTrail audit, Transactions transactions) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.people = people;
        this.holidays = holidays;
        this.workplaces = workplaces;
        this.audit = audit;
        this.transactions = transactions;
    }

    @Override
    public Schedule createSchedule(Function<String, Schedule> factory, String actor) {
        Schedule schedule = factory.apply("SCH-" + UUID.randomUUID().toString().substring(0, 8));
        return transactions.inTransaction(() -> {
            schedules.save(schedule);
            audit.record(Actors.normalize(actor), "SCHEDULE_CREATED", "schedule", schedule.id(),
                    schedule.name() + ": " + schedule.summary());
            return schedule;
        });
    }

    @Override
    public List<Schedule> listSchedules() {
        return schedules.findAll().stream().sorted(Comparator.comparing(Schedule::name)).toList();
    }

    @Override
    public Schedule getSchedule(String id) {
        return schedules.findById(Require.text(id, "scheduleId", 64))
                .orElseThrow(() -> new ScheduleNotFoundException("No existe el horario '" + id + "'"));
    }

    @Override
    public ScheduleAssignment assign(AssignScheduleCommand command) {
        ScheduleAssignment assignment = new ScheduleAssignment("ASG-" + UUID.randomUUID().toString().substring(0, 8),
                command.scheduleId(), command.targetType(), command.targetId(), command.validFrom(),
                command.validTo(), command.workplaceId());
        return transactions.inTransaction(() -> {
            Schedule schedule = getSchedule(assignment.scheduleId());
            validateTarget(assignment);
            if (assignment.workplaceId() != null && workplaces.findById(assignment.workplaceId()).isEmpty())
                throw new WorkplaceNotFoundException("No existe el centro de trabajo '" + assignment.workplaceId() + "'");
            assignments.findAll().stream()
                    .filter(other -> other.sameTarget(assignment) && other.overlaps(assignment))
                    .findFirst()
                    .ifPresent(other -> {
                        throw new AssignmentOverlapException("El periodo se solapa con la asignacion " + other.id()
                                + " (" + other.validFrom() + " - " + (other.validTo() == null ? "sin fin" : other.validTo())
                                + ") del mismo destino");
                    });
            assignments.save(assignment);
            audit.record(Actors.normalize(command.actor()), "SCHEDULE_ASSIGNED", "assignment", assignment.id(),
                    schedule.name() + " -> " + assignment.targetType() + " " + assignment.targetId() + " desde "
                            + assignment.validFrom() + (assignment.validTo() == null ? "" : " hasta " + assignment.validTo())
                            + (assignment.workplaceId() == null ? "" : " en " + assignment.workplaceId()));
            return assignment;
        });
    }

    private void validateTarget(ScheduleAssignment assignment) {
        if (assignment.targetType() == TargetType.PERSON) {
            if (people.findById(assignment.targetId()).isEmpty())
                throw new PersonNotFoundException("No existe ninguna persona con id '" + assignment.targetId() + "'");
        } else if (people.findAll().stream().noneMatch(p -> p.belongsTo(assignment.targetId()))) {
            throw new InvalidDataException("No hay ninguna persona en el equipo '" + assignment.targetId() + "'");
        }
    }

    @Override
    public List<ScheduleAssignment> listAssignments(String personId) {
        List<ScheduleAssignment> all = assignments.findAll();
        if (personId == null || personId.isBlank())
            return all.stream().sorted(Comparator.comparing(ScheduleAssignment::validFrom)).toList();
        Person person = people.findById(personId.strip())
                .orElseThrow(() -> new PersonNotFoundException("No existe ninguna persona con id '" + personId + "'"));
        return all.stream().filter(a -> a.appliesTo(person))
                .sorted(Comparator.comparing(ScheduleAssignment::validFrom)).toList();
    }

    @Override
    public Holiday addHoliday(Holiday holiday, String actor) {
        return transactions.inTransaction(() -> {
            if (holidays.findByDate(holiday.date()).isPresent())
                throw new HolidayAlreadyExistsException("Ya hay un festivo el " + holiday.date());
            holidays.save(holiday);
            audit.record(Actors.normalize(actor), "HOLIDAY_CREATED", "holiday", holiday.date().toString(),
                    holiday.description());
            return holiday;
        });
    }

    @Override
    public List<Holiday> listHolidays() {
        return holidays.findAll().stream().sorted(Comparator.comparing(Holiday::date)).toList();
    }
}
