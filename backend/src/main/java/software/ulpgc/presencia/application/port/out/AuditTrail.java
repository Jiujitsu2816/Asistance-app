package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.audit.AuditEntry;

import java.util.List;

/** Registro de auditoria de las modificaciones (solo anadir). */
public interface AuditTrail {
    void record(String actor, String action, String entityType, String entityId, String detail);

    List<AuditEntry> recent(int limit);
}
