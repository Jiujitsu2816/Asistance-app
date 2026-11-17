package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.attendance.IncidentJustification;

/** Idea propia: justificar una incidencia calculada (cita medica, formacion...). */
public interface JustifyIncidentUseCase {
    record JustifyIncidentCommand(String incidentKey, String reason, String actor) { }

    IncidentJustification justify(JustifyIncidentCommand command);
}
