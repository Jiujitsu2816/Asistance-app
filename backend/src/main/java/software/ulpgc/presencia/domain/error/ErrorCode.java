package software.ulpgc.presencia.domain.error;

/** Codigos de error del contrato. Se documentan en el README. */
public enum ErrorCode {
    INVALID_DATA,
    PERSON_NOT_FOUND,
    PERSON_ALREADY_EXISTS,
    SCHEDULE_NOT_FOUND,
    SCHEDULE_NOT_ASSIGNED,
    DATE_OUT_OF_PERIOD,
    ASSIGNMENT_OVERLAP,
    DUPLICATE_CLOCK_EVENT,
    INCONSISTENT_CLOCK_EVENT,
    HOLIDAY_ALREADY_EXISTS,
    INCIDENT_NOT_FOUND,
    INCIDENT_ALREADY_JUSTIFIED
}
