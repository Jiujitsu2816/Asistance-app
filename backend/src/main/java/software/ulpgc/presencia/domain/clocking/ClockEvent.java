package software.ulpgc.presencia.domain.clocking;

import software.ulpgc.presencia.domain.Require;

import java.time.Instant;

/**
 * Fichaje. Es inmutable y solo se anade (nunca se modifica ni se borra), de modo que el
 * historial es auditable.
 *
 * @param occurredAt momento del fichaje.
 * @param recordedAt  momento en que el sistema lo registro; difiere de occurredAt en
 *                    fichajes introducidos a posteriori.
 * @param workplaceId centro de trabajo de la asignacion vigente (null si no tiene centro).
 */
public record ClockEvent(String id, String personId, ClockEventType type, Instant occurredAt,
                         Instant recordedAt, ClockSource source, String note, String workplaceId) {
    public ClockEvent {
        id = Require.text(id, "id", 64);
        personId = Require.text(personId, "personId", 32);
        Require.present(type, "type");
        Require.present(occurredAt, "timestamp");
        Require.present(recordedAt, "recordedAt");
        Require.present(source, "source");
        note = Require.optionalText(note, "note", 200);
        workplaceId = Require.optionalText(workplaceId, "workplaceId", 32);
    }
}
