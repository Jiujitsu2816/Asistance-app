package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.UserAccountRepository;
import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcUserAccountRepository implements UserAccountRepository {
    private final Database db;

    public JdbcUserAccountRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(UserAccount a) {
        db.run(c -> {
            try (var st = c.prepareStatement("INSERT INTO user_account "
                    + "(username, role, person_id, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")) {
                st.setString(1, a.username());
                st.setString(2, a.role().name());
                st.setString(3, a.personId());
                st.setString(4, a.passwordHash());
                Jdbc.setInstant(st, 5, a.createdAt());
                return st.executeUpdate();
            }
        });
    }

    @Override
    public void updatePassword(String username, String passwordHash) {
        db.run(c -> {
            try (var st = c.prepareStatement("UPDATE user_account SET password_hash = ? WHERE username = ?")) {
                st.setString(1, passwordHash);
                st.setString(2, username);
                return st.executeUpdate();
            }
        });
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM user_account WHERE username = ?")) {
                st.setString(1, username);
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<UserAccount> findAll() {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT * FROM user_account ORDER BY username");
                 ResultSet rs = st.executeQuery()) {
                List<UserAccount> result = new ArrayList<>();
                while (rs.next()) result.add(map(rs));
                return result;
            }
        });
    }

    @Override
    public boolean existsWithRole(Role role) {
        return db.run(c -> {
            try (var st = c.prepareStatement("SELECT 1 FROM user_account WHERE role = ? LIMIT 1")) {
                st.setString(1, role.name());
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private static UserAccount map(ResultSet rs) throws SQLException {
        return new UserAccount(rs.getString("username"), Role.valueOf(rs.getString("role")), rs.getString("person_id"),
                rs.getString("password_hash"), Jdbc.instant(rs, "created_at"));
    }
}
