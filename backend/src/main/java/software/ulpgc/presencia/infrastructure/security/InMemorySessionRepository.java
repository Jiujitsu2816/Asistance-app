package software.ulpgc.presencia.infrastructure.security;

import software.ulpgc.presencia.application.port.out.SessionRepository;
import software.ulpgc.presencia.application.security.Session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Sesiones en memoria: al reiniciar el backend hay que volver a iniciar sesion. */
public final class InMemorySessionRepository implements SessionRepository {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override public void save(Session session) { sessions.put(session.token(), session); }

    @Override public Optional<Session> find(String token) { return Optional.ofNullable(sessions.get(token)); }

    @Override public void delete(String token) { sessions.remove(token); }

    @Override public void deleteByUsername(String username) {
        sessions.values().removeIf(s -> s.username().equals(username));
    }
}
