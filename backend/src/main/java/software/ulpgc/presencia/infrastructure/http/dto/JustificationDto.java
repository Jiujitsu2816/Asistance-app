package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.attendance.IncidentJustification;

import java.time.OffsetDateTime;

public record JustificationDto(String incidentKey, String personId, String reason, String justifiedBy,
                               OffsetDateTime justifiedAt) {
    public static JustificationDto from(IncidentJustification j, Times t) {
        return j == null ? null : new JustificationDto(j.incidentKey(), j.personId(), j.reason(), j.justifiedBy(),
                t.of(j.justifiedAt()));
    }
}
