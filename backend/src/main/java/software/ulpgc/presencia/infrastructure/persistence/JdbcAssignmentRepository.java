package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.TargetType;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class JdbcAssignmentRepository implements AssignmentRepository {
    private final Database db;

    public JdbcAssignmentRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(ScheduleAssignment a) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO schedule_assignment "
                    + "(id, schedule_id, target_type, target_id, valid_from, valid_to, workplace_id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, a.id());
                st.setString(2, a.scheduleId());
                st.setString(3, a.targetType().name());
                st.setString(4, a.targetId());
                Jdbc.setDate(st, 5, a.validFrom());
                Jdbc.setDate(st, 6, a.validTo());
                st.setString(7, a.workplaceId());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public List<ScheduleAssignment> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM schedule_assignment ORDER BY valid_from, id");
                 ResultSet rs = st.executeQuery()) {
                List<ScheduleAssignment> result = new ArrayList<>();
                while (rs.next())
                    result.add(new ScheduleAssignment(rs.getString("id"), rs.getString("schedule_id"),
                            TargetType.valueOf(rs.getString("target_type")), rs.getString("target_id"),
                            Jdbc.date(rs, "valid_from"), Jdbc.date(rs, "valid_to"),
                            rs.getString("workplace_id")));
                return result;
            }
        });
    }
}
