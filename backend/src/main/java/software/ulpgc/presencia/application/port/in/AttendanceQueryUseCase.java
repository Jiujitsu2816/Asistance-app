package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.attendance.DailyAttendance;
import software.ulpgc.presencia.domain.attendance.Incident;
import software.ulpgc.presencia.domain.attendance.IncidentType;
import software.ulpgc.presencia.domain.attendance.PresenceState;
import software.ulpgc.presencia.domain.audit.AuditEntry;
import software.ulpgc.presencia.domain.clocking.ClockEvent;

import java.time.LocalDate;
import java.util.List;

/** Consultas de presencia, registros, incidencias y auditoria. */
public interface AttendanceQueryUseCase {
    /** Rango maximo de dias de una consulta. */
    int MAX_RANGE_DAYS = 366;

    List<PresenceState> currentPresence();

    PresenceState presenceOf(String personId);

    /** personId null = todas las personas. */
    List<ClockEvent> clockEvents(String personId, LocalDate from, LocalDate to);

    List<DailyAttendance> dailyReport(String personId, LocalDate from, LocalDate to);

    /** personId y type opcionales (null = sin filtro). */
    List<Incident> incidents(String personId, LocalDate from, LocalDate to, IncidentType type);

    List<AuditEntry> audit(int limit);
}
