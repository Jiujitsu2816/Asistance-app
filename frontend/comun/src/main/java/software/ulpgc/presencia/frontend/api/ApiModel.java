package software.ulpgc.presencia.frontend.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tipos del contrato HTTP vistos desde los frontends. Son independientes de las clases del
 * backend: los frontends solo conocen el JSON documentado en el README. Se ignoran los
 * campos desconocidos para tolerar ampliaciones compatibles de la API.
 */
public final class ApiModel {
    private ApiModel() { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Person(String id, String displayName, String team) {
        @Override public String toString() {
            return id + " · " + displayName;
        }
    }

    /** Horario: segun kind se usan unos campos u otros (ver README, seccion 8). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Schedule(String id, String name, String kind, Integer toleranceMinutes,
                           List<DayOfWeek> workDays, List<Slot> slots,
                           LocalDate cycleStart, List<CycleEntry> cycle,
                           LocalTime start, Integer durationMinutes, String mode, List<LocalDate> dates,
                           String summary) {
        @Override public String toString() {
            return name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slot(LocalTime start, LocalTime end) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CycleEntry(Boolean rest, String label, LocalTime start, Integer durationMinutes) {
        public static CycleEntry restDay() {
            return new CycleEntry(true, null, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Assignment(String id, String scheduleId, String targetType, String targetId,
                             LocalDate validFrom, LocalDate validTo) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AssignmentRequest(String scheduleId, String personId, String team, LocalDate validFrom,
                                    LocalDate validTo) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Holiday(LocalDate date, String description) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClockRequest(String personId, String type, String source, OffsetDateTime timestamp,
                               String note) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClockEvent(String id, String personId, String type, OffsetDateTime occurredAt,
                             OffsetDateTime recordedAt, String source, String note) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClockResult(ClockEvent event, Presence presence) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Presence(String personId, String displayName, String status, boolean inside,
                           OffsetDateTime since, String currentPeriod, OffsetDateTime currentPeriodStart,
                           OffsetDateTime currentPeriodEnd, String todayStatus) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Report(String personId, String displayName, LocalDate from, LocalDate to, long expectedMinutes,
                         long workedMinutes, long deviationMinutes, int incidents, List<Day> days) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Day(String personId, LocalDate date, String scheduleId, String scheduleName, String holiday,
                      String status, long expectedMinutes, long workedMinutes, Long deviationMinutes,
                      boolean openSession, List<Period> periods, List<Incident> incidents) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Period(String label, OffsetDateTime expectedStart, OffsetDateTime expectedEnd,
                         boolean presenceRequired, OffsetDateTime firstEntry, OffsetDateTime lastExit,
                         long workedMinutes, boolean open) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Incident(String key, String personId, LocalDate date, String type, String periodLabel,
                           OffsetDateTime expectedAt, OffsetDateTime actualAt, long minutes, String description,
                           boolean justified, Justification justification) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Justification(String incidentKey, String personId, String reason, String justifiedBy,
                                OffsetDateTime justifiedAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuditEntry(long sequence, OffsetDateTime at, String actor, String action, String entityType,
                             String entityId, String detail) { }
}
