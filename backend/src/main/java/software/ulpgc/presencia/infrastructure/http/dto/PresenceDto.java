package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.attendance.DayStatus;
import software.ulpgc.presencia.domain.attendance.PresenceState;
import software.ulpgc.presencia.domain.attendance.PresenceStatus;

import java.time.OffsetDateTime;

public record PresenceDto(String personId, String displayName, PresenceStatus status, boolean inside,
                          OffsetDateTime since, String currentPeriod, OffsetDateTime currentPeriodStart,
                          OffsetDateTime currentPeriodEnd, DayStatus todayStatus) {
    public static PresenceDto from(PresenceState s, String displayName, Times t) {
        return new PresenceDto(s.personId(), displayName, s.status(), s.inside(), t.of(s.since()), s.currentPeriod(),
                t.of(s.currentPeriodStart()), t.of(s.currentPeriodEnd()), s.todayStatus());
    }
}
