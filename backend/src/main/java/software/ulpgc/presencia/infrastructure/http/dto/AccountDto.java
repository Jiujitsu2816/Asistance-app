package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;

import java.time.OffsetDateTime;

/** Cuenta sin datos de la clave. */
public record AccountDto(String username, Role role, String personId, OffsetDateTime createdAt) {
    public static AccountDto from(UserAccount a, Times t) {
        return new AccountDto(a.username(), a.role(), a.personId(), t.of(a.createdAt()));
    }
}
