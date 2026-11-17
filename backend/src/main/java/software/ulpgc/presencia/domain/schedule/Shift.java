package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.Duration;
import java.time.LocalTime;

/** Turno: hora de inicio y duracion. Puede cruzar la medianoche (turno de noche). */
public record Shift(String label, LocalTime start, Duration duration) {
    public Shift {
        label = Require.text(label, "label", 40);
        Require.present(start, "start");
        Require.present(duration, "durationMinutes");
        Require.that(duration.toMinutes() > 0 && duration.compareTo(Duration.ofHours(24)) <= 0,
                "La duracion del turno debe estar entre 1 minuto y 24 horas");
    }

    /** Minutos desde el inicio del dia en que empieza hasta que termina. */
    long endMinuteOfStartDay() {
        return start.toSecondOfDay() / 60L + duration.toMinutes();
    }
}
