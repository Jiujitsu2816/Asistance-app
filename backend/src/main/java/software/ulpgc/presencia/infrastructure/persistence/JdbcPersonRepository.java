package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.domain.person.Person;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcPersonRepository implements PersonRepository {
    private final Database db;

    public JdbcPersonRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Person person) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO person (id, display_name, team) VALUES (?, ?, ?)")) {
                st.setString(1, person.id());
                st.setString(2, person.displayName());
                st.setString(3, person.team());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Person> findById(String id) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM person WHERE id = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Person> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM person ORDER BY id"); ResultSet rs = st.executeQuery()) {
                List<Person> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        });
    }

    private static Person map(ResultSet rs) throws SQLException {
        return new Person(rs.getString("id"), rs.getString("display_name"), rs.getString("team"));
    }
}
