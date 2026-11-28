package software.ulpgc.presencia.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase.RegisterPersonCommand;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase.RegisterClockEventCommand;
import software.ulpgc.presencia.application.port.in.RegisterClockEventUseCase;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase.AssignScheduleCommand;
import software.ulpgc.presencia.application.port.in.ScheduleAdministrationUseCase;
import software.ulpgc.presencia.application.service.AttendanceQueryService;
import software.ulpgc.presencia.application.service.PersonAdministrationService;
import software.ulpgc.presencia.application.service.RegisterClockEventService;
import software.ulpgc.presencia.application.service.ScheduleAdministrationService;
import software.ulpgc.presencia.domain.Fixtures;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.attendance.DayStatus;
import software.ulpgc.presencia.domain.attendance.Incident;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.attendance.PresenceStatus;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.domain.schedule.Schedule;
import software.ulpgc.presencia.support.InMemoryAdapters;
import software.ulpgc.presencia.support.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;
import static software.ulpgc.presencia.support.Errors.assertError;

/** Casos de uso ejecutados con adaptadores en memoria. */
class UseCasesTest {
    private MutableClock clock;
    private InMemoryAdapters adapters;
    private PersonAdministrationUseCase people;
    private ScheduleAdministrationUseCase schedules;
    private RegisterClockEventUseCase clocking;
    private AttendanceQueryUseCase queries;
    private Schedule office;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(at(MONDAY, 7, 0), ZONE);
        adapters = new InMemoryAdapters(clock);
        var personRepo = adapters.personRepository();
        var scheduleRepo = adapters.scheduleRepository();
        var assignmentRepo = adapters.assignmentRepository();
        var holidayRepo = adapters.holidayRepository();
        var eventRepo = adapters.clockEventRepository();
        var audit = adapters.auditTrail();
        var tx = adapters.transactions();
        people = new PersonAdministrationService(personRepo, audit, tx);
        schedules = new ScheduleAdministrationService(scheduleRepo, assignmentRepo, personRepo, holidayRepo,
                adapters.workplaceRepository(), audit, tx);
        clocking = new RegisterClockEventService(personRepo, eventRepo, assignmentRepo, audit, tx, clock, ZONE);
        queries = new AttendanceQueryService(personRepo, scheduleRepo, assignmentRepo, holidayRepo, eventRepo,
                adapters.workplaceRepository(), audit, clock, ZONE);

        people.register(new RegisterPersonCommand("EMP-1", "Persona Uno", "oficina", "test"));
        people.register(new RegisterPersonCommand("EMP-2", "Persona Dos", "oficina", "test"));
        people.register(new RegisterPersonCommand("EMP-3", "Persona Tres", null, "test"));
        office = schedules.createSchedule(id -> new software.ulpgc.presencia.domain.schedule.ContinuousSchedule(id,
                "Oficina", Duration.ofMinutes(10), WEEKDAYS, Fixtures.office().slot()), "test");
    }

    private void assignTeam(LocalDate from, LocalDate to) {
        schedules.assign(new AssignScheduleCommand(office.id(), TargetType.TEAM, "oficina", from, to, null, "test"));
    }

    private void clockAt(String person, ClockEventType type, Instant at) {
        clock.set(at);
        clocking.register(new RegisterClockEventCommand(person, type, ClockSource.KIOSK, null, null, "kiosko"));
    }

    @Test
    void fullDayFlowWithLateArrival() {
        assignTeam(MONDAY, null);
        clockAt("EMP-1", ClockEventType.ENTRY, at(MONDAY, 8, 30));
        assertEquals(PresenceStatus.PRESENT, queries.presenceOf("EMP-1").state().status());
        clockAt("EMP-1", ClockEventType.EXIT, at(MONDAY, 15, 0));
        clock.set(at(MONDAY, 20, 0));

        var day = queries.dailyReport("EMP-1", MONDAY, MONDAY).getFirst();
        assertEquals(DayStatus.LATE, day.status());
        assertEquals(390, day.workedMinutes());
        assertEquals(-30L, day.deviationMinutes());
        assertEquals(2, queries.clockEvents("EMP-1", MONDAY, MONDAY).size());
        assertEquals(List.of("EMP-1 LATE_ARRIVAL", "EMP-2 ABSENCE"), queries.incidents(null, MONDAY, MONDAY, null)
                .stream().map(i -> i.personId() + " " + i.type()).toList());
        assertEquals(1, queries.incidents(null, MONDAY, MONDAY, IncidentType.ABSENCE).size());
    }

    @Test
    void clockingErrors() {
        assignTeam(MONDAY, MONDAY.plusDays(2));
        assertError(ErrorCode.PERSON_NOT_FOUND, () -> clockAt("NOPE", ClockEventType.ENTRY, at(MONDAY, 8, 0)));
        assertError(ErrorCode.SCHEDULE_NOT_ASSIGNED, () -> clockAt("EMP-3", ClockEventType.ENTRY, at(MONDAY, 8, 0)));
        assertError(ErrorCode.INCONSISTENT_CLOCK_EVENT, () -> clockAt("EMP-1", ClockEventType.EXIT, at(MONDAY, 8, 0)));
        clockAt("EMP-1", ClockEventType.ENTRY, at(MONDAY, 8, 0));
        assertError(ErrorCode.DUPLICATE_CLOCK_EVENT, () -> clockAt("EMP-1", ClockEventType.ENTRY, at(MONDAY, 8, 1)));
        assertError(ErrorCode.DATE_OUT_OF_PERIOD, () -> clockAt("EMP-2", ClockEventType.ENTRY, at(MONDAY.plusDays(3), 8, 0)));
        assertError(ErrorCode.INVALID_DATA, () -> clocking.register(
                new RegisterClockEventCommand("EMP-1", null, ClockSource.KIOSK, null, null, "x")));
        assertEquals(1, adapters.events.size(), "los fichajes rechazados no se guardan");
    }

    @Test
    void personalAssignmentHasPriorityOverTeamAssignment() {
        assignTeam(MONDAY, null);
        Schedule restaurant = schedules.createSchedule(id -> new software.ulpgc.presencia.domain.schedule.SplitSchedule(
                id, "Restaurante", Duration.ofMinutes(5), WEEKDAYS, Fixtures.restaurant().slots()), "test");
        schedules.assign(new AssignScheduleCommand(restaurant.id(), TargetType.PERSON, "EMP-1", MONDAY, MONDAY, null, "t"));
        clock.set(at(MONDAY, 23, 59));
        assertEquals("Restaurante", queries.dailyReport("EMP-1", MONDAY, MONDAY).getFirst().scheduleName());
        assertEquals("Oficina", queries.dailyReport("EMP-2", MONDAY, MONDAY).getFirst().scheduleName());
        assertEquals("Oficina", queries.dailyReport("EMP-1", MONDAY.plusDays(1), MONDAY.plusDays(1))
                .getFirst().scheduleName());
    }

    @Test
    void assignmentValidation() {
        assignTeam(MONDAY, MONDAY.plusDays(10));
        assertError(ErrorCode.ASSIGNMENT_OVERLAP, () -> assignTeam(MONDAY.plusDays(10), null));
        assertDoesNotThrow(() -> assignTeam(MONDAY.plusDays(11), null));
        assertError(ErrorCode.SCHEDULE_NOT_FOUND, () -> schedules.assign(
                new AssignScheduleCommand("NOPE", TargetType.PERSON, "EMP-1", MONDAY, null, null, "t")));
        assertError(ErrorCode.PERSON_NOT_FOUND, () -> schedules.assign(
                new AssignScheduleCommand(office.id(), TargetType.PERSON, "NOPE", MONDAY, null, null, "t")));
        assertError(ErrorCode.INVALID_DATA, () -> schedules.assign(
                new AssignScheduleCommand(office.id(), TargetType.TEAM, "inexistente", MONDAY, null, null, "t")));
        assertError(ErrorCode.INVALID_DATA, () -> schedules.assign(
                new AssignScheduleCommand(office.id(), TargetType.PERSON, "EMP-3", MONDAY, MONDAY.minusDays(1), null, "t")));
    }

    @Test
    void duplicatedPeopleAndHolidaysAreRejected() {
        assertError(ErrorCode.PERSON_ALREADY_EXISTS, () -> people.register(new RegisterPersonCommand("EMP-1", "Otra", null, "t")));
        schedules.addHoliday(new Holiday(MONDAY, "Festivo"), "t");
        assertError(ErrorCode.HOLIDAY_ALREADY_EXISTS, () -> schedules.addHoliday(new Holiday(MONDAY, "Otro"), "t"));
    }

    @Test
    void holidaysChangeTheOfficeDay() {
        assignTeam(MONDAY, null);
        schedules.addHoliday(new Holiday(MONDAY, "Festivo local"), "rrhh");
        clock.set(at(MONDAY, 20, 0));
        assertEquals(DayStatus.HOLIDAY, queries.dailyReport("EMP-1", MONDAY, MONDAY).getFirst().status());
    }

    @Test
    void everyChangeIsAudited() {
        assignTeam(MONDAY, null);
        clockAt("EMP-1", ClockEventType.ENTRY, at(MONDAY, 8, 0));
        assertEquals(List.of("CLOCK_ENTRY", "SCHEDULE_ASSIGNED", "SCHEDULE_CREATED", "PERSON_CREATED"),
                queries.audit(4).stream().map(e -> e.action()).toList());
        assertEquals("kiosko", queries.audit(1).getFirst().actor());
    }

    @Test
    void queryRangeIsValidated() {
        assertError(ErrorCode.INVALID_DATA, () -> queries.dailyReport("EMP-1", MONDAY, MONDAY.minusDays(1)));
        assertError(ErrorCode.INVALID_DATA, () -> queries.dailyReport("EMP-1", MONDAY, MONDAY.plusDays(400)));
        assertError(ErrorCode.PERSON_NOT_FOUND, () -> queries.dailyReport("NOPE", MONDAY, MONDAY));
    }
}
