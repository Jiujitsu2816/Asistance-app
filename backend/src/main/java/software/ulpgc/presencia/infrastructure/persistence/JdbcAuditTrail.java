package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.domain.audit.AuditEntry;

import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class JdbcAuditTrail implements AuditTrail {
    private final Database db;
    private final Clock clock;

    public JdbcAuditTrail(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    @Override
    public void record(String actor, String action, String entityType, String entityId, String detail) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO audit_log "
                    + "(logged_at, actor, action, entity_type, entity_id, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
                Jdbc.setInstant(st, 1, clock.instant());
                st.setString(2, actor);
                st.setString(3, action);
                st.setString(4, entityType);
                st.setString(5, entityId);
                st.setString(6, detail == null || detail.length() <= 400 ? detail : detail.substring(0, 400));
                return st.executeUpdate();
            }
        });
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM audit_log ORDER BY seq DESC LIMIT ?")) {
                st.setInt(1, limit);
                try (ResultSet rs = st.executeQuery()) {
                    List<AuditEntry> result = new ArrayList<>();
                    while (rs.next())
                        result.add(new AuditEntry(rs.getLong("seq"), Jdbc.instant(rs, "logged_at"),
                                rs.getString("actor"), rs.getString("action"), rs.getString("entity_type"),
                                rs.getString("entity_id"), rs.getString("detail")));
                    return result;
                }
            }
        });
    }
}
