package software.ulpgc.presencia.frontend.api;

import software.ulpgc.presencia.frontend.api.ApiModel.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de comunicacion de los frontends con el backend. Las vistas solo dependen de esta
 * interfaz; {@link HttpPresenceApi} es la implementacion HTTP + JSON y se puede sustituir
 * (por ejemplo, por un doble de prueba) sin tocar las vistas.
 *
 * <p>Todos los metodos son bloqueantes y lanzan {@link ApiException} con el codigo de error
 * del contrato: las vistas deben llamarlos fuera del hilo de JavaFX.
 */
public interface PresenceApi {
    // Personas
    List<Person> listPeople();

    Person createPerson(Person person);

    // Horarios, asignaciones y festivos
    List<Schedule> listSchedules();

    Schedule createSchedule(Schedule schedule);

    List<Assignment> listAssignments();

    Assignment assignSchedule(AssignmentRequest request);

    List<Holiday> listHolidays();

    Holiday createHoliday(Holiday holiday);

    // Fichajes
    ClockResult clock(ClockRequest request);

    /** personId null = todas las personas. */
    List<ClockEvent> listClockEvents(String personId, LocalDate from, LocalDate to);

    // Consultas
    List<Presence> currentPresence();

    Presence presenceOf(String personId);

    Report dailyReport(String personId, LocalDate from, LocalDate to);

    /** personId y type opcionales (null = sin filtro). */
    List<Incident> listIncidents(String personId, LocalDate from, LocalDate to, String type);

    Justification justifyIncident(String incidentKey, String reason);

    List<AuditEntry> audit(int limit);
}
