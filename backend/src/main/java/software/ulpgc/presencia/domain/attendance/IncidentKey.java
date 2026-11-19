package software.ulpgc.presencia.domain.attendance;

import software.ulpgc.presencia.domain.error.IncidentNotFoundException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Clave estable de una incidencia calculada: persona|fecha|tipo|hora de referencia.
 * Las incidencias no se guardan (se recalculan a partir de los fichajes), pero la clave
 * permite asociarles una justificacion persistente.
 */
public record IncidentKey(String personId, LocalDate date, IncidentType type, LocalTime reference) {

    public String value() {
        return personId + "|" + date + "|" + type + "|" + reference;
    }

    public static IncidentKey parse(String value) {
        String[] parts = value == null ? new String[0] : value.split("\\|");
        if (parts.length != 4) throw new IncidentNotFoundException("Incidencia desconocida: " + value);
        try {
            return new IncidentKey(parts[0], LocalDate.parse(parts[1]), IncidentType.valueOf(parts[2]),
                    LocalTime.parse(parts[3]));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new IncidentNotFoundException("Incidencia desconocida: " + value);
        }
    }
}
