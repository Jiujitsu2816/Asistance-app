package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.WorkplaceRepository;
import software.ulpgc.presencia.domain.workplace.Workplace;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcWorkplaceRepository implements WorkplaceRepository {
    private final Database db;

    public JdbcWorkplaceRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Workplace w) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO workplace (id, name, address) VALUES (?, ?, ?)")) {
                st.setString(1, w.id());
                st.setString(2, w.name());
                st.setString(3, w.address());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Workplace> findById(String id) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM workplace WHERE id = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Workplace> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM workplace ORDER BY name"); ResultSet rs = st.executeQuery()) {
                List<Workplace> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        });
    }

    private static Workplace map(ResultSet rs) throws SQLException {
        return new Workplace(rs.getString("id"), rs.getString("name"), rs.getString("address"));
    }
}
