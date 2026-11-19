package software.ulpgc.presencia.domain;

import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.domain.attendance.AttendanceEvaluator;
import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.DayStatus;
import software.ulpgc.presencia.domain.attendance.Incident;
import software.ulpgc.presencia.domain.attendance.IncidentJustification;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.attendance.PresenceStatus;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.calendar.HolidayCalendar;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.Schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;

class AttendanceEvaluatorTest {
    private final AttendanceEvaluator evaluator = new AttendanceEvaluator(ZONE);

    private AttendanceEvaluator.Input input(Schedule schedule, List<ClockEvent> events) {
        return input(schedule, events, HolidayCalendar.empty(), Map.of());
    }

    private AttendanceEvaluator.Input input(Schedule schedule, List<ClockEvent> events, HolidayCalendar holidays,
                                            Map<String, IncidentJustification> justifications) {
        return new AttendanceEvaluator.Input("EMP-1", date -> Optional.of(schedule), holidays, events, justifications);
    }

    private DailyAttendance day(Schedule schedule, LocalDate date, Instant now, ClockEvent... events) {
        return evaluator.evaluate(input(schedule, List.of(events)), date, date, now).getFirst();
    }

    private static List<IncidentType> types(DailyAttendance day) {
        return day.incidents().stream().map(Incident::type).toList();
    }

    // ---- Horario corrido -------------------------------------------------------------

    @Test
    void officeDayOnTimeIsCompletedWithoutDeviation() {
        DailyAttendance day = day(office(), MONDAY, at(MONDAY, 20, 0),
                entry("EMP-1", at(MONDAY, 8, 5)), exit("EMP-1", at(MONDAY, 15, 5)));
        assertEquals(DayStatus.COMPLETED, day.status());
        assertEquals(420, day.expectedMinutes());
        assertEquals(420, day.workedMinutes());
        assertEquals(0L, day.deviationMinutes());
        assertTrue(day.incidents().isEmpty(), "5 min de retraso entran en la tolerancia de 10");
    }

    @Test
    void lateArrivalBeyondToleranceIsAnIncident() {
        DailyAttendance day = day(office(), MONDAY, at(MONDAY, 20, 0),
                entry("EMP-1", at(MONDAY, 8, 25)), exit("EMP-1", at(MONDAY, 15, 0)));
        assertEquals(DayStatus.LATE, day.status());
        Incident late = day.incidents().getFirst();
        assertEquals(IncidentType.LATE_ARRIVAL, late.type());
        assertEquals(25, late.minutes());
        assertEquals(-25L, day.deviationMinutes());
        assertEquals("EMP-1|2026-11-16|LATE_ARRIVAL|08:00", late.key());
    }

    @Test
    void earlyDepartureIsDetectedOnlyOnceThePeriodEnded() {
        ClockEvent in = entry("EMP-1", at(MONDAY, 8, 0));
        ClockEvent out = exit("EMP-1", at(MONDAY, 13, 0));
        assertEquals(DayStatus.IN_PROGRESS, day(office(), MONDAY, at(MONDAY, 14, 0), in, out).status(),
                "a las 14:00 aun puede volver");
        DailyAttendance after = day(office(), MONDAY, at(MONDAY, 16, 0), in, out);
        assertEquals(DayStatus.EARLY_DEPARTURE, after.status());
        assertEquals(120, after.incidents().getFirst().minutes());
    }

    @Test
    void absenceAfterToleranceAndPendingBefore() {
        assertEquals(DayStatus.PENDING, day(office(), MONDAY, at(MONDAY, 7, 0)).status());
        assertTrue(day(office(), MONDAY, at(MONDAY, 8, 5)).incidents().isEmpty(), "dentro de la tolerancia");
        DailyAttendance absent = day(office(), MONDAY, at(MONDAY, 9, 0));
        assertEquals(DayStatus.ABSENT, absent.status());
        assertEquals(List.of(IncidentType.ABSENCE), types(absent));
        assertNull(absent.deviationMinutes(), "el dia no ha terminado");
    }

    @Test
    void missingCheckoutWhenThePeriodEndedWithAnOpenEntry() {
        DailyAttendance day = day(office(), MONDAY, at(MONDAY, 18, 0), entry("EMP-1", at(MONDAY, 8, 0)));
        assertEquals(DayStatus.CHECKOUT_PENDING, day.status());
        assertEquals(List.of(IncidentType.MISSING_CHECKOUT), types(day));
        assertEquals(0, day.workedMinutes(), "una entrada abierta no suma horas");
        assertTrue(day.openSession());
    }

    @Test
    void holidaysCancelOfficeWorkButNotShifts() {
        var holidays = new HolidayCalendar(List.of(new Holiday(MONDAY, "Festivo de prueba")));
        DailyAttendance office = evaluator.evaluate(input(office(), List.of(), holidays, Map.of()),
                MONDAY, MONDAY, at(MONDAY, 20, 0)).getFirst();
        assertEquals(DayStatus.HOLIDAY, office.status());
        assertEquals("Festivo de prueba", office.holiday());
        DailyAttendance shift = evaluator.evaluate(input(hospitalShifts(), List.of(), holidays, Map.of()),
                MONDAY, MONDAY, at(MONDAY, 20, 0)).getFirst();
        assertEquals(DayStatus.ABSENT, shift.status());
    }

    @Test
    void workOnARestDayIsExtraWork() {
        LocalDate saturday = MONDAY.plusDays(5);
        DailyAttendance day = day(office(), saturday, at(saturday, 20, 0),
                entry("EMP-1", at(saturday, 9, 0)), exit("EMP-1", at(saturday, 11, 0)));
        assertEquals(DayStatus.EXTRA_WORK, day.status());
        assertEquals(120, day.workedMinutes());
        assertEquals(120L, day.deviationMinutes());
    }

    @Test
    void noAssignmentMeansNoSchedule() {
        var input = new AttendanceEvaluator.Input("EMP-1", date -> Optional.empty(), HolidayCalendar.empty(),
                List.of(), Map.of());
        DailyAttendance day = evaluator.evaluate(input, MONDAY, MONDAY, at(MONDAY, 20, 0)).getFirst();
        assertEquals(DayStatus.NO_SCHEDULE, day.status());
        assertNull(day.deviationMinutes());
    }

    // ---- Horario partido ---------------------------------------------------------------

    @Test
    void splitScheduleEvaluatesEachSlot() {
        LocalDate tuesday = MONDAY.plusDays(1);
        DailyAttendance day = day(restaurant(), tuesday, at(tuesday, 23, 59),
                entry("EMP-1", at(tuesday, 11, 58)), exit("EMP-1", at(tuesday, 16, 2)),
                entry("EMP-1", at(tuesday, 19, 50)), exit("EMP-1", at(tuesday, 23, 30)));
        assertEquals(DayStatus.LATE, day.status());
        assertEquals(List.of(IncidentType.LATE_ARRIVAL), types(day));
        assertEquals("19:30-23:30", day.incidents().getFirst().periodLabel());
        assertEquals(2, day.periods().size());
        assertEquals(480, day.expectedMinutes());
        assertEquals(244 + 220, day.workedMinutes());
    }

    @Test
    void splitScheduleWithoutReturningAfterTheBreakIsAnAbsenceOfTheSecondSlot() {
        LocalDate tuesday = MONDAY.plusDays(1);
        DailyAttendance day = day(restaurant(), tuesday, at(tuesday, 23, 59),
                entry("EMP-1", at(tuesday, 12, 0)), exit("EMP-1", at(tuesday, 16, 0)));
        assertEquals(DayStatus.ABSENT, day.status());
        assertEquals("19:30-23:30", day.incidents().getFirst().periodLabel());
    }

    // ---- Turnos y guardias ---------------------------------------------------------------

    @Test
    void nightShiftCrossingMidnightBelongsToTheDayItStarts() {
        LocalDate wednesday = MONDAY.plusDays(2);
        LocalDate thursday = wednesday.plusDays(1);
        List<ClockEvent> events = List.of(entry("EMP-1", at(wednesday, 22, 55)), exit("EMP-1", at(thursday, 7, 0)));
        List<DailyAttendance> days = evaluator.evaluate(input(hospitalShifts(), events), wednesday, thursday,
                at(thursday, 12, 0));
        assertEquals(DayStatus.COMPLETED, days.get(0).status());
        assertEquals(485, days.get(0).workedMinutes());
        assertEquals(DayStatus.REST, days.get(1).status());
        assertEquals(0, days.get(1).workedMinutes());
    }

    @Test
    void physicalOnCallRequiresPresenceForTwentyFourHours() {
        DailyAttendance day = day(onCall(OnCallMode.PHYSICAL, MONDAY), MONDAY, at(MONDAY.plusDays(1), 10, 0),
                entry("EMP-1", at(MONDAY, 8, 0)), exit("EMP-1", at(MONDAY, 20, 0)));
        assertEquals(DayStatus.EARLY_DEPARTURE, day.status());
        assertEquals(1440, day.expectedMinutes());
    }

    @Test
    void localizedOnCallNeverProducesAbsencesButCountsActivations() {
        var schedule = onCall(OnCallMode.LOCALIZED, MONDAY);
        DailyAttendance idle = day(schedule, MONDAY, at(MONDAY.plusDays(1), 10, 0));
        assertTrue(idle.incidents().isEmpty());
        assertEquals(0, idle.expectedMinutes());
        DailyAttendance called = day(schedule, MONDAY, at(MONDAY.plusDays(1), 10, 0),
                entry("EMP-1", at(MONDAY, 22, 0)), exit("EMP-1", at(MONDAY, 23, 30)));
        assertEquals(DayStatus.COMPLETED, called.status());
        assertEquals(90, called.workedMinutes());
    }

    // ---- Justificaciones (idea propia) ------------------------------------------------

    @Test
    void justifiedIncidentsDoNotDegradeTheStatus() {
        String key = "EMP-1|2026-11-16|ABSENCE|08:00";
        var justification = new IncidentJustification(key, "EMP-1", "Cita medica", "rrhh", at(MONDAY, 10, 0));
        DailyAttendance day = evaluator.evaluate(input(office(), List.of(), HolidayCalendar.empty(),
                Map.of(key, justification)), MONDAY, MONDAY, at(MONDAY, 20, 0)).getFirst();
        assertEquals(DayStatus.JUSTIFIED, day.status());
        assertTrue(day.incidents().getFirst().justified());
    }

    // ---- Presencia actual -------------------------------------------------------------

    @Test
    void presenceReflectsTheCurrentPeriod() {
        var empty = input(office(), List.of());
        assertEquals(PresenceStatus.EXPECTED, evaluator.presence(empty, at(MONDAY, 8, 5)).status());
        assertEquals(PresenceStatus.ABSENT, evaluator.presence(empty, at(MONDAY, 9, 0)).status());
        assertEquals(PresenceStatus.OFF_DUTY, evaluator.presence(empty, at(MONDAY, 18, 0)).status());

        var in = input(office(), List.of(entry("EMP-1", at(MONDAY, 8, 0))));
        assertEquals(PresenceStatus.PRESENT, evaluator.presence(in, at(MONDAY, 9, 0)).status());
        assertEquals(PresenceStatus.CHECKOUT_PENDING, evaluator.presence(in, at(MONDAY, 18, 0)).status());

        var none = new AttendanceEvaluator.Input("EMP-1", d -> Optional.empty(), HolidayCalendar.empty(),
                List.of(), Map.of());
        assertEquals(PresenceStatus.NO_SCHEDULE, evaluator.presence(none, at(MONDAY, 9, 0)).status());
    }

    @Test
    void anEntryJustRegisteredCountsAsPresentAndLate() {
        Instant entry = at(MONDAY, 11, 19);
        var in = input(office(), List.of(entry("EMP-1", entry)));
        assertEquals(PresenceStatus.PRESENT, evaluator.presence(in, entry).status(), "mismo instante");
        assertEquals(DayStatus.LATE, evaluator.presence(in, entry).todayStatus());
    }

    @Test
    void anEarlyArrivalIsAssociatedWithTheNextPeriod() {
        var in = input(office(), List.of(entry("EMP-1", at(MONDAY, 7, 50))));
        var state = evaluator.presence(in, at(MONDAY, 7, 55));
        assertEquals(PresenceStatus.PRESENT, state.status());
        DailyAttendance day = evaluator.evaluate(in, MONDAY, MONDAY, at(MONDAY, 7, 55)).getFirst();
        assertEquals(DayStatus.IN_PROGRESS, day.status());
        assertTrue(day.incidents().isEmpty(), "llegar antes no es trabajo fuera de horario");
    }

    @Test
    void presenceDuringANightShiftThatStartedYesterday() {
        LocalDate wednesday = MONDAY.plusDays(2);
        var in = input(hospitalShifts(), List.of(entry("EMP-1", at(wednesday, 23, 0))));
        var state = evaluator.presence(in, at(wednesday.plusDays(1), 3, 0));
        assertEquals(PresenceStatus.PRESENT, state.status());
        assertEquals("Noche", state.currentPeriod());
    }

    @Test
    void anEntryForgottenYesterdayIsAPendingCheckoutEvenDuringTodaysPeriod() {
        LocalDate tuesday = MONDAY.plusDays(1);
        var in = input(office(), List.of(entry("EMP-1", at(MONDAY, 8, 0))));
        var state = evaluator.presence(in, at(tuesday, 10, 0));
        assertEquals(PresenceStatus.CHECKOUT_PENDING, state.status());
        assertEquals(DayStatus.ABSENT, state.todayStatus());
        var older = input(office(), List.of(entry("EMP-1", at(MONDAY.minusDays(7), 8, 0))));
        assertEquals(PresenceStatus.CHECKOUT_PENDING, evaluator.presence(older, at(tuesday, 10, 0)).status(),
                "tambien si la entrada queda fuera de la ventana evaluada");
    }

    @Test
    void presenceOnLocalizedOnCall() {
        var input = input(onCall(OnCallMode.LOCALIZED, MONDAY), List.of());
        assertEquals(PresenceStatus.ON_CALL, evaluator.presence(input, at(MONDAY, 12, 0)).status());
    }
}
