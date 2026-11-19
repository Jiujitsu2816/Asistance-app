package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.JustificationRepository;
import software.ulpgc.presencia.domain.attendance.IncidentJustification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class JdbcJustificationRepository implements JustificationRepository {
    private final Database db;

    public JdbcJustificationRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(IncidentJustification j) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO incident_justification "
                    + "(incident_key, person_id, reason, justified_by, justified_at) VALUES (?, ?, ?, ?, ?)")) {
                st.setString(1, j.incidentKey());
                st.setString(2, j.personId());
                st.setString(3, j.reason());
                st.setString(4, j.justifiedBy());
                Jdbc.setInstant(st, 5, j.justifiedAt());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<IncidentJustification> findByKey(String incidentKey) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM incident_justification WHERE incident_key = ?")) {
                st.setString(1, incidentKey);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Map<String, IncidentJustification> findByPerson(String personId) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM incident_justification WHERE person_id = ?")) {
                st.setString(1, personId);
                try (ResultSet rs = st.executeQuery()) {
                    Map<String, IncidentJustification> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        IncidentJustification j = map(rs);
                        result.put(j.incidentKey(), j);
                    }
                    return result;
                }
            }
        });
    }

    private static IncidentJustification map(ResultSet rs) throws SQLException {
        return new IncidentJustification(rs.getString("incident_key"), rs.getString("person_id"),
                rs.getString("reason"), rs.getString("justified_by"), Jdbc.instant(rs, "justified_at"));
    }
}
