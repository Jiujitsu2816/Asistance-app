package software.ulpgc.presencia.domain.attendance;

import software.ulpgc.presencia.domain.Require;

import java.time.Instant;

/** Justificacion registrada sobre una incidencia calculada (idea propia). */
public record IncidentJustification(String incidentKey, String personId, String reason,
                                    String justifiedBy, Instant justifiedAt) {
    public IncidentJustification {
        incidentKey = Require.text(incidentKey, "incidentKey", 160);
        personId = Require.text(personId, "personId", 32);
        reason = Require.text(reason, "reason", 200);
        justifiedBy = Require.text(justifiedBy, "justifiedBy", 40);
        Require.present(justifiedAt, "justifiedAt");
    }
}
