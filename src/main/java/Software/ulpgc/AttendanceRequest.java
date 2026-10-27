package Software.ulpgc;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The requested time is preserved even when a manager decides later. */
public record AttendanceRequest(UUID id, Employe employee, Place place, Type type,
                                Instant requestedAt, Status status, String decidedBy,
                                Instant decidedAt, String reason) {
    public enum Type { CHECK_IN, CHECK_OUT }
    public enum Status { PENDING, APPROVED, DENIED }

    public AttendanceRequest {
        Objects.requireNonNull(id);
        Objects.requireNonNull(employee);
        Objects.requireNonNull(place);
        Objects.requireNonNull(type);
        Objects.requireNonNull(requestedAt);
        Objects.requireNonNull(status);
        reason = Objects.requireNonNull(reason);
        if (status == Status.PENDING && (decidedBy != null || decidedAt != null))
            throw new IllegalArgumentException("Una solicitud pendiente no tiene decision");
        if (status != Status.PENDING) {
            Objects.requireNonNull(decidedBy);
            Objects.requireNonNull(decidedAt);
            if (decidedAt.isBefore(requestedAt))
                throw new IllegalArgumentException("La decision no puede ser anterior a la solicitud");
        }
        if (status == Status.DENIED && reason.isBlank())
            throw new IllegalArgumentException("Indica el motivo de la denegacion");
    }
}
