package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.account.Role;

/** personId es obligatorio para EMPLOYEE. */
public record AccountRequest(String username, String password, Role role, String personId) { }
