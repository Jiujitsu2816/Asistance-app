package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;

import java.time.OffsetDateTime;

public record ClockEventDto(String id, String personId, ClockEventType type, OffsetDateTime occurredAt,
                            OffsetDateTime recordedAt, ClockSource source, String note, String workplaceId) {
    public static ClockEventDto from(ClockEvent e, Times times) {
        return new ClockEventDto(e.id(), e.personId(), e.type(), times.of(e.occurredAt()), times.of(e.recordedAt()),
                e.source(), e.note(), e.workplaceId());
    }
}
