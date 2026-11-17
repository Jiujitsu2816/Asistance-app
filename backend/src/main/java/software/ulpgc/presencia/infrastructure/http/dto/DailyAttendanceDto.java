package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.DayStatus;
import software.ulpgc.presencia.domain.attendance.PeriodResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyAttendanceDto(String personId, LocalDate date, String scheduleId, String scheduleName,
                                 String holiday, DayStatus status, long expectedMinutes, long workedMinutes,
                                 Long deviationMinutes, boolean openSession, List<PeriodDto> periods,
                                 List<IncidentDto> incidents) {

    public record PeriodDto(String label, OffsetDateTime expectedStart, OffsetDateTime expectedEnd,
                            boolean presenceRequired, OffsetDateTime firstEntry, OffsetDateTime lastExit,
                            long workedMinutes, boolean open) {
        static PeriodDto from(PeriodResult p, Times t) {
            return new PeriodDto(p.label(), t.of(p.expectedStart()), t.of(p.expectedEnd()), p.presenceRequired(),
                    t.of(p.firstEntry()), t.of(p.lastExit()), p.workedMinutes(), p.open());
        }
    }

    public static DailyAttendanceDto from(DailyAttendance d, Times t) {
        return new DailyAttendanceDto(d.personId(), d.date(), d.scheduleId(), d.scheduleName(), d.holiday(),
                d.status(), d.expectedMinutes(), d.workedMinutes(), d.deviationMinutes(), d.openSession(),
                d.periods().stream().map(p -> PeriodDto.from(p, t)).toList(),
                d.incidents().stream().map(i -> IncidentDto.from(i, t)).toList());
    }
}
