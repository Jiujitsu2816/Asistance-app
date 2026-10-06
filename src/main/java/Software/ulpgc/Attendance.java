package Software.ulpgc;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** An immutable work session; a null checkOut means the session is still open. */
public record Attendance(Employe employee, Place place, Instant checkIn, Instant checkOut) {
    public Attendance {
        Objects.requireNonNull(employee, "Employee is required");
        Objects.requireNonNull(place, "Place is required");
        Objects.requireNonNull(checkIn, "Check-in time is required");
        if (checkOut != null && checkOut.isBefore(checkIn))
            throw new IllegalArgumentException("Check-out cannot precede check-in");
    }

    public boolean isOpen() {
        return checkOut == null;
    }

    public Duration workedDuration() {
        if (isOpen()) throw new IllegalStateException("Check out before calculating worked time");
        return Duration.between(checkIn, checkOut);
    }
}
