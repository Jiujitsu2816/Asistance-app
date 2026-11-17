package software.ulpgc.presencia.domain.attendance;

import java.time.LocalDate;
import java.util.List;

/**
 * Resumen diario de una persona.
 *
 * @param deviationMinutes trabajado - esperado; null mientras el dia no ha terminado
 *                         (periodos pendientes o una entrada abierta).
 */
public record DailyAttendance(String personId, LocalDate date, String scheduleId, String scheduleName,
                              String holiday, DayStatus status, long expectedMinutes, long workedMinutes,
                              Long deviationMinutes, boolean openSession, List<PeriodResult> periods,
                              List<Incident> incidents) { }
