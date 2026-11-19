package software.ulpgc.presencia.domain.clocking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Sesion de trabajo: una entrada y, si existe, su salida. */
public record WorkSession(ClockEvent entry, ClockEvent exit) {

    public Instant start() {
        return entry.occurredAt();
    }

    public Optional<Instant> end() {
        return Optional.ofNullable(exit).map(ClockEvent::occurredAt);
    }

    public boolean isOpen() {
        return exit == null;
    }

    public Duration worked() {
        return exit == null ? Duration.ZERO : Duration.between(entry.occurredAt(), exit.occurredAt());
    }

    /** Empareja fichajes ordenados en sesiones. Los eventos huerfanos se ignoran. */
    public static List<WorkSession> pair(List<ClockEvent> orderedEvents) {
        var sessions = new java.util.ArrayList<WorkSession>();
        ClockEvent open = null;
        for (ClockEvent event : orderedEvents) {
            if (event.type() == ClockEventType.ENTRY) {
                if (open != null) sessions.add(new WorkSession(open, null));
                open = event;
            } else if (open != null) {
                sessions.add(new WorkSession(open, event));
                open = null;
            }
        }
        if (open != null) sessions.add(new WorkSession(open, null));
        return List.copyOf(sessions);
    }
}
