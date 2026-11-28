package software.ulpgc.presencia.domain;

import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.domain.schedule.CycleDay;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.RotatingShiftSchedule;
import software.ulpgc.presencia.domain.schedule.Shift;
import software.ulpgc.presencia.domain.schedule.SplitSchedule;
import software.ulpgc.presencia.domain.schedule.TimeSlot;
import software.ulpgc.presencia.domain.schedule.WorkPeriod;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;
import static software.ulpgc.presencia.support.Errors.assertError;

class ScheduleTypesTest {

    @Test
    void continuousScheduleHasOneSlotOnWorkDaysOnly() {
        List<WorkPeriod> monday = office().periodsStartingOn(MONDAY);
        assertEquals(1, monday.size());
        assertEquals(Duration.ofHours(7), monday.getFirst().duration());
        assertTrue(office().periodsStartingOn(MONDAY.plusDays(5)).isEmpty(), "sabado sin jornada");
        assertTrue(office().exemptOnHolidays());
    }

    @Test
    void splitScheduleHasTwoSlotsAndRequiresABreak() {
        assertEquals(2, restaurant().periodsStartingOn(MONDAY.plusDays(1)).size());
        assertTrue(restaurant().periodsStartingOn(MONDAY).isEmpty(), "el lunes cierra");
        assertError(ErrorCode.INVALID_DATA, () -> new SplitSchedule("x", "x", Duration.ZERO, WEEKDAYS,
                List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(14, 0)),
                        new TimeSlot(LocalTime.of(14, 0), LocalTime.of(18, 0)))));
        assertError(ErrorCode.INVALID_DATA, () -> new SplitSchedule("x", "x", Duration.ZERO, WEEKDAYS,
                List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(14, 0)))));
    }

    @Test
    void slotMustEndAfterItStarts() {
        assertError(ErrorCode.INVALID_DATA, () -> new TimeSlot(LocalTime.of(15, 0), LocalTime.of(8, 0)));
    }

    @Test
    void rotatingShiftsRepeatTheCycleAlsoBeforeItsStart() {
        RotatingShiftSchedule shifts = hospitalShifts();
        assertEquals("Manana", shifts.periodsStartingOn(MONDAY).getFirst().label());
        assertEquals("Noche", shifts.periodsStartingOn(MONDAY.plusDays(2)).getFirst().label());
        assertTrue(shifts.periodsStartingOn(MONDAY.plusDays(3)).isEmpty());
        assertEquals("Manana", shifts.periodsStartingOn(MONDAY.plusDays(5)).getFirst().label());
        assertTrue(shifts.periodsStartingOn(MONDAY.minusDays(1)).isEmpty(), "el dia anterior es descanso");
        assertEquals("Noche", shifts.periodsStartingOn(MONDAY.minusDays(3)).getFirst().label());
        assertFalse(shifts.exemptOnHolidays(), "los turnos mantienen la cobertura en festivos");
    }

    @Test
    void rotatingShiftsCannotOverlapTheNextDay() {
        var night = new CycleDay.Work(new Shift("Noche", LocalTime.of(23, 0), Duration.ofHours(9)));
        var morning = new CycleDay.Work(new Shift("Manana", LocalTime.of(7, 0), Duration.ofHours(8)));
        assertError(ErrorCode.INVALID_DATA, () -> new RotatingShiftSchedule("x", "x", Duration.ZERO,
                MONDAY, List.of(night, morning)));
    }

    @Test
    void onCallOnlyOnItsDatesAndLocalizedDoesNotRequirePresence() {
        var physical = onCall(OnCallMode.PHYSICAL, MONDAY);
        assertTrue(physical.periodsStartingOn(MONDAY).getFirst().presenceRequired());
        assertTrue(physical.periodsStartingOn(MONDAY.plusDays(1)).isEmpty());
        assertFalse(onCall(OnCallMode.LOCALIZED, MONDAY).periodsStartingOn(MONDAY).getFirst().presenceRequired());
    }

    @Test
    void toleranceIsBounded() {
        assertError(ErrorCode.INVALID_DATA, () -> new SplitSchedule("x", "x", Duration.ofMinutes(-1),
                WEEKDAYS, restaurant().slots()));
    }
}
