package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.attendance.Incident;
import software.ulpgc.presencia.domain.attendance.IncidentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record IncidentDto(String key, String personId, LocalDate date, IncidentType type, String periodLabel,
                          OffsetDateTime expectedAt, OffsetDateTime actualAt, long minutes, String description,
                          boolean justified, JustificationDto justification) {
    public static IncidentDto from(Incident i, Times t) {
        return new IncidentDto(i.key(), i.personId(), i.date(), i.type(), i.periodLabel(), t.of(i.expectedAt()),
                t.of(i.actualAt()), i.minutes(), i.description(), i.justified(),
                JustificationDto.from(i.justification(), t));
    }
}
