package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.application.security.Principal;
import software.ulpgc.presencia.domain.account.Role;

/** Usuario con sesion iniciada. */
public record UserDto(String username, Role role, String personId, String displayName) {
    public static UserDto from(Principal p) {
        return new UserDto(p.username(), p.role(), p.personId(), p.displayName());
    }
}
