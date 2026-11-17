package software.ulpgc.presencia.infrastructure.http.dto;

import java.time.LocalDate;
import java.util.List;

/** Informe de una persona en un periodo con totales. */
public record ReportDto(String personId, String displayName, LocalDate from, LocalDate to, long expectedMinutes,
                        long workedMinutes, long deviationMinutes, int incidents, List<DailyAttendanceDto> days) { }
