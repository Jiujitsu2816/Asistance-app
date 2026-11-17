package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.JustificationRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignments;
import software.ulpgc.presencia.domain.attendance.AttendanceEvaluator;
import software.ulpgc.presencia.domain.attendance.ScheduleLookup;
import software.ulpgc.presencia.domain.calendar.HolidayCalendar;
import software.ulpgc.presencia.domain.error.PersonNotFoundException;
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
    private final JustificationRepository justifications;
    private final ZoneId zone;

    AttendanceContext(PersonRepository people, ScheduleRepository schedules, AssignmentRepository assignments,
                      HolidayRepository holidays, ClockEventRepository events,
                      JustificationRepository justifications, ZoneId zone) {
        this.people = people;
        this.schedules = schedules;
        this.assignments = assignments;
        this.holidays = holidays;
        this.events = events;
        this.justifications = justifications;
        this.zone = zone;
    }

    Person person(String personId) {
        if (personId == null || personId.isBlank())
            throw new software.ulpgc.presencia.domain.error.InvalidDataException("Falta el campo obligatorio 'personId'");
        return people.findById(personId.strip())
                .orElseThrow(() -> new PersonNotFoundException("No existe ninguna persona con id '" + personId + "'"));
    }

    java.util.List<Person> allPeople() {
        return people.findAll();
    }

    AttendanceEvaluator.Input input(Person person, LocalDate from, LocalDate to) {
        return new AttendanceEvaluator.Input(person.id(), lookup(person), holidayCalendar(),
                events.find(person.id(), startOf(AttendanceEvaluator.eventsFrom(from)),
                        startOf(AttendanceEvaluator.eventsToExclusive(to))),
                justifications.findByPerson(person.id()));
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
