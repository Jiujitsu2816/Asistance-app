package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.audit.AuditEntry;

import java.time.OffsetDateTime;

public record AuditDto(long sequence, OffsetDateTime at, String actor, String action, String entityType,
                       String entityId, String detail) {
    public static AuditDto from(AuditEntry e, Times t) {
        return new AuditDto(e.sequence(), t.of(e.at()), e.actor(), e.action(), e.entityType(), e.entityId(),
                e.detail());
    }
}
