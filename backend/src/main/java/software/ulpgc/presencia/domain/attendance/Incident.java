package software.ulpgc.presencia.domain.attendance;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Incidencia calculada al evaluar los fichajes contra el horario.
 *
 * @param expectedAt hora esperada (inicio o fin del periodo), si aplica.
 * @param actualAt   hora real fichada, si aplica.
 * @param minutes    magnitud de la desviacion en minutos (retraso, adelanto...).
 */
public record Incident(String key, String personId, LocalDate date, IncidentType type, String periodLabel,
                       Instant expectedAt, Instant actualAt, long minutes, String description,
                       IncidentJustification justification) {

    public boolean justified() {
        return justification != null;
    }
}
