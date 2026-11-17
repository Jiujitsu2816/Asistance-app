package software.ulpgc.presencia.domain.audit;

import java.time.Instant;

/** Registro de auditoria de una modificacion: quien, cuando, que y sobre que entidad. */
public record AuditEntry(long sequence, Instant at, String actor, String action, String entityType,
                         String entityId, String detail) { }
