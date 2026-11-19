package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.attendance.IncidentJustification;

import java.util.Map;
import java.util.Optional;

public interface JustificationRepository {
    void save(IncidentJustification justification);

    Optional<IncidentJustification> findByKey(String incidentKey);

    /** Justificaciones de una persona indexadas por clave de incidencia. */
    Map<String, IncidentJustification> findByPerson(String personId);
}
