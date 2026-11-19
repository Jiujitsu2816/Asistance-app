package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.application.security.Principal;

import java.time.Instant;

/** Inicio y cierre de sesion. */
public interface AuthenticationUseCase {
    record LoginResult(String token, Instant expiresAt, Principal principal) { }

    /** true mientras no exista ninguna cuenta de responsable (primer arranque). */
    boolean setupRequired();

    /** Crea la primera cuenta de responsable. Solo se permite si setupRequired(). */
    LoginResult setup(String username, char[] password);

    LoginResult login(String username, char[] password);

    void logout(String token);

    /** Usuario de un token valido; lanza UNAUTHENTICATED si no lo es. */
    Principal authenticate(String token);
}
