package software.ulpgc.presencia.infrastructure.persistence;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Conversiones de tipos java.time a JDBC. Los instantes se guardan en UTC. */
final class Jdbc {
    private Jdbc() { }

    static void setInstant(PreparedStatement statement, int index, Instant instant) throws SQLException {
        statement.setObject(index, instant == null ? null : instant.atOffset(ZoneOffset.UTC));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    static void setDate(PreparedStatement statement, int index, LocalDate date) throws SQLException {
        statement.setObject(index, date == null ? null : Date.valueOf(date));
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }
}
