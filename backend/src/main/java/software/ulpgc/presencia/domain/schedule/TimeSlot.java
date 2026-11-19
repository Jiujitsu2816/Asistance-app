package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.Duration;
import java.time.LocalTime;

/** Tramo horario dentro de un mismo dia (no cruza la medianoche). */
public record TimeSlot(LocalTime start, LocalTime end) {
    public TimeSlot {
        Require.minutePrecision(start, "start");
        Require.minutePrecision(end, "end");
        Require.that(end.isAfter(start),
                "El tramo " + start + "-" + end + " debe terminar despues de empezar el mismo dia");
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public String label() {
        return start + "-" + end;
    }
}
