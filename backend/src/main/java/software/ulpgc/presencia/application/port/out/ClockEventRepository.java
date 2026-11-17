package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.clocking.ClockEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Registro de solo anadir: no ofrece operaciones de modificacion ni de borrado. */
public interface ClockEventRepository {
    void append(ClockEvent event);

    Optional<ClockEvent> findLast(String personId);

    /** Fichajes con occurredAt en [from, to), ordenados cronologicamente. personId null = todos. */
    List<ClockEvent> find(String personId, Instant from, Instant to);
}
