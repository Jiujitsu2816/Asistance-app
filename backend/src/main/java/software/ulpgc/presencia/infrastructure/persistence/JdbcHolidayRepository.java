package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.domain.calendar.Holiday;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcHolidayRepository implements HolidayRepository {
    private final Database db;

    public JdbcHolidayRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Holiday holiday) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO holiday (holiday_date, description) VALUES (?, ?)")) {
                Jdbc.setDate(st, 1, holiday.date());
                st.setString(2, holiday.description());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Holiday> findByDate(LocalDate date) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM holiday WHERE holiday_date = ?")) {
                Jdbc.setDate(st, 1, date);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(new Holiday(Jdbc.date(rs, "holiday_date"),
                            rs.getString("description"))) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Holiday> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM holiday ORDER BY holiday_date");
                 ResultSet rs = st.executeQuery()) {
                List<Holiday> result = new ArrayList<>();
                while (rs.next()) result.add(new Holiday(Jdbc.date(rs, "holiday_date"), rs.getString("description")));
                return result;
            }
        });
    }
}
