package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.application.security.Session;

import java.util.Optional;

/** Sesiones abiertas (tokens de acceso). */
public interface SessionRepository {
    void save(Session session);

    Optional<Session> find(String token);

    void delete(String token);

    void deleteByUsername(String username);
}
