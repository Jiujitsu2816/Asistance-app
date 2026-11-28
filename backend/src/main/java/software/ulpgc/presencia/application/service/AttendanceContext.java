package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignments;
import software.ulpgc.presencia.domain.attendance.AttendanceEvaluator;
import software.ulpgc.presencia.domain.attendance.ScheduleLookup;
import software.ulpgc.presencia.domain.calendar.HolidayCalendar;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.domain.person.Person;
import software.ulpgc.presencia.domain.schedule.Schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reune, a traves de los puertos de salida, los datos que necesita el evaluador. */
final class AttendanceContext {
    private final PersonRepository people;
    private final ScheduleRepository schedules;
    private final AssignmentRepository assignments;
    private final HolidayRepository holidays;
    private final ClockEventRepository events;
    private final ZoneId zone;

    AttendanceContext(PersonRepository people, ScheduleRepository schedules, AssignmentRepository assignments,
                      HolidayRepository holidays, ClockEventRepository events, ZoneId zone) {
        this.people = people;
        this.schedules = schedules;
        this.assignments = assignments;
        this.holidays = holidays;
        this.events = events;
        this.zone = zone;
    }

    Person person(String personId) {
        if (personId == null || personId.isBlank())
            throw new DomainException(ErrorCode.INVALID_DATA, "Falta el campo obligatorio 'personId'");
        return people.findById(personId.strip())
                .orElseThrow(() -> new DomainException(ErrorCode.PERSON_NOT_FOUND, "No existe ninguna persona con id '" + personId + "'"));
    }

    java.util.List<Person> allPeople() {
        return people.findAll();
    }

    AttendanceEvaluator.Input input(Person person, LocalDate from, LocalDate to) {
        return new AttendanceEvaluator.Input(person.id(), lookup(person), holidayCalendar(),
                events.find(person.id(), startOf(AttendanceEvaluator.eventsFrom(from)),
                        startOf(AttendanceEvaluator.eventsToExclusive(to))));
    }

    java.util.Optional<software.ulpgc.presencia.domain.assignment.ScheduleAssignment> assignmentOn(Person person,
                                                                                                  LocalDate date) {
        return new ScheduleAssignments(person, assignments.findAll()).on(date);
    }

    private ScheduleLookup lookup(Person person) {
        ScheduleAssignments resolved = new ScheduleAssignments(person, assignments.findAll());
        Map<String, Schedule> byId = schedules.findAll().stream()
                .collect(Collectors.toMap(Schedule::id, Function.identity()));
        return date -> resolved.on(date).map(a -> byId.get(a.scheduleId()));
    }

    private HolidayCalendar holidayCalendar() {
        return new HolidayCalendar(holidays.findAll());
    }

    java.time.Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }
}
