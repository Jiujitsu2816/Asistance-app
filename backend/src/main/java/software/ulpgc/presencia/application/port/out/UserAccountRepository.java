package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository {
    void save(UserAccount account);

    void updatePassword(String username, String passwordHash);

    Optional<UserAccount> findByUsername(String username);

    List<UserAccount> findAll();

    boolean existsWithRole(Role role);
}
