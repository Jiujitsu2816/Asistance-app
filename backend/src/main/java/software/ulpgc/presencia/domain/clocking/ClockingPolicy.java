package software.ulpgc.presencia.domain.clocking;

import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Reglas de coherencia de la secuencia de fichajes de una persona:
 * <ul>
 *   <li>ENTRADA y SALIDA se alternan: una segunda entrada sin salida es un fichaje duplicado.</li>
 *   <li>No se admite una salida sin entrada previa abierta.</li>
 *   <li>Los fichajes se anaden en orden cronologico: no pueden ser anteriores al ultimo.</li>
 *   <li>No se admiten fichajes en el futuro (con un margen de reloj de 1 minuto).</li>
 * </ul>
 */
public final class ClockingPolicy {
    public static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zone;

    public ClockingPolicy(ZoneId zone) {
        this.zone = zone;
    }

    public void check(Optional<ClockEvent> last, ClockEventType type, Instant at, Instant now) {
        if (at.isAfter(now.plus(CLOCK_SKEW)))
            throw new DomainException(ErrorCode.INVALID_DATA, "No se puede fichar en el futuro (" + format(at) + ")");
        if (last.isPresent() && at.isBefore(last.get().occurredAt()))
            throw new DomainException(ErrorCode.INCONSISTENT_CLOCK_EVENT, "El fichaje (" + format(at)
                    + ") es anterior al ultimo registrado (" + format(last.get().occurredAt()) + ")");
        boolean open = last.isPresent() && last.get().type() == ClockEventType.ENTRY;
        if (type == ClockEventType.ENTRY && open)
            throw new DomainException(ErrorCode.DUPLICATE_CLOCK_EVENT, "Ya existe una entrada abierta desde "
                    + format(last.get().occurredAt()) + "; registra antes la salida");
        if (type == ClockEventType.EXIT && !open)
            throw new DomainException(ErrorCode.INCONSISTENT_CLOCK_EVENT, "Salida sin entrada previa: no hay ninguna entrada abierta");
    }

    private String format(Instant instant) {
        return FORMAT.format(instant.atZone(zone));
    }
}
