package software.ulpgc.presencia.domain.schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Periodo de trabajo esperado que empieza en una fecha concreta. Puede terminar al dia
 * siguiente (turno de noche o guardia de 24 h).
 *
 * @param presenceRequired false en guardias localizadas: no se exige fichar.
 */
public record WorkPeriod(String label, LocalDate date, LocalTime start, Duration duration,
                         boolean presenceRequired) {

    public LocalDateTime startDateTime() {
        return date.atTime(start);
    }
}
