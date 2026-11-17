package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;

import java.time.Instant;

/** Fichar entrada o salida. */
public interface RegisterClockEventUseCase {
    /**
     * @param timestamp momento del fichaje; null usa la hora actual del servidor.
     */
    record RegisterClockEventCommand(String personId, ClockEventType type, ClockSource source,
                                     Instant timestamp, String note, String actor) { }

    ClockEvent register(RegisterClockEventCommand command);
}
