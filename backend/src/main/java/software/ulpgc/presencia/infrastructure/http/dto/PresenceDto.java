package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.application.port.in.AttendanceQueryUseCase.PersonPresence;
import software.ulpgc.presencia.domain.attendance.DayStatus;
import software.ulpgc.presencia.domain.attendance.PresenceState;
import software.ulpgc.presencia.domain.attendance.PresenceStatus;

import java.time.OffsetDateTime;

public record PresenceDto(String personId, String displayName, PresenceStatus status, boolean inside,
                          OffsetDateTime since, String currentPeriod, OffsetDateTime currentPeriodStart,
                          OffsetDateTime currentPeriodEnd, DayStatus todayStatus, WorkplaceDto workplace) {
    public static PresenceDto from(PersonPresence p, String displayName, Times t) {
        PresenceState s = p.state();
        return new PresenceDto(s.personId(), displayName, s.status(), s.inside(), t.of(s.since()), s.currentPeriod(),
                t.of(s.currentPeriodStart()), t.of(s.currentPeriodEnd()), s.todayStatus(),
                WorkplaceDto.from(p.workplace()));
    }
}
