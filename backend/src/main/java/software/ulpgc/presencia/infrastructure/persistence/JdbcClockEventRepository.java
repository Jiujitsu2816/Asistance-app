package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Solo inserta y consulta: los fichajes no se actualizan ni se borran. */
public final class JdbcClockEventRepository implements ClockEventRepository {
    private static final String ORDER = " ORDER BY occurred_at, recorded_at, seq";
    private final Database db;

    public JdbcClockEventRepository(Database db) {
        this.db = db;
    }

    @Override
    public void append(ClockEvent e) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO clock_event "
                    + "(id, person_id, event_type, occurred_at, recorded_at, source, note, workplace_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, e.id());
                st.setString(2, e.personId());
                st.setString(3, e.type().name());
                Jdbc.setInstant(st, 4, e.occurredAt());
                Jdbc.setInstant(st, 5, e.recordedAt());
                st.setString(6, e.source().name());
                st.setString(7, e.note());
                st.setString(8, e.workplaceId());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<ClockEvent> findLast(String personId) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM clock_event WHERE person_id = ? "
                    + "ORDER BY occurred_at DESC, recorded_at DESC, seq DESC LIMIT 1")) {
                st.setString(1, personId);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<ClockEvent> find(String personId, Instant from, Instant to) {
        String sql = "SELECT * FROM clock_event WHERE occurred_at >= ? AND occurred_at < ?"
                + (personId == null ? "" : " AND person_id = ?") + ORDER;
        return db.run(c -> {
            try (var st = c.prepareStatement(sql)) {
                Jdbc.setInstant(st, 1, from);
                Jdbc.setInstant(st, 2, to);
                if (personId != null) st.setString(3, personId);
                try (ResultSet rs = st.executeQuery()) {
                    List<ClockEvent> result = new ArrayList<>();
                    while (rs.next()) result.add(map(rs));
                    return result;
                }
            }
        });
    }

    private static ClockEvent map(ResultSet rs) throws SQLException {
        return new ClockEvent(rs.getString("id"), rs.getString("person_id"),
                ClockEventType.valueOf(rs.getString("event_type")), Jdbc.instant(rs, "occurred_at"),
                Jdbc.instant(rs, "recorded_at"), ClockSource.valueOf(rs.getString("source")), rs.getString("note"),
                rs.getString("workplace_id"));
    }
}
