package software.ulpgc.presencia.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.domain.schedule.Schedule;
import software.ulpgc.presencia.infrastructure.json.ScheduleDocument;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cada tipo de horario tiene una estructura distinta, por lo que la definicion completa se
 * guarda como JSON en la columna definition; nombre, tipo y tolerancia se guardan tambien
 * como columnas para poder consultarlos con SQL.
 */
public final class JdbcScheduleRepository implements ScheduleRepository {
    private final Database db;
    private final ObjectMapper json;

    public JdbcScheduleRepository(Database db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Override
    public void save(Schedule schedule) {
        String definition = write(ScheduleDocument.from(schedule));
        db.run(c -> {
            try (var st = c.prepareStatement(
                    "INSERT INTO schedule (id, name, kind, tolerance_minutes, definition) VALUES (?, ?, ?, ?, ?)")) {
                st.setString(1, schedule.id());
                st.setString(2, schedule.name());
                st.setString(3, schedule.kind().name());
                st.setInt(4, (int) schedule.tolerance().toMinutes());
                st.setString(5, definition);
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Schedule> findById(String id) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT id, definition FROM schedule WHERE id = ?")) {
                st.setString(1, id);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Schedule> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT id, definition FROM schedule ORDER BY name");
                 ResultSet rs = st.executeQuery()) {
                List<Schedule> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        });
    }

    private Schedule map(ResultSet rs) throws SQLException {
        try {
            return json.readValue(rs.getString("definition"), ScheduleDocument.class).toDomain(rs.getString("id"));
        } catch (JsonProcessingException e) {
            throw new PersistenceException("Definicion de horario corrupta: " + rs.getString("id"), e);
        }
    }

    private String write(ScheduleDocument document) {
        try {
            return json.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new PersistenceException("No se pudo serializar el horario", e);
        }
    }
}
