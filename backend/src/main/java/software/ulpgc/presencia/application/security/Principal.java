package software.ulpgc.presencia.application.security;

import software.ulpgc.presencia.domain.account.Role;

/** Usuario autenticado que realiza una peticion. */
public record Principal(String username, Role role, String personId, String displayName) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
