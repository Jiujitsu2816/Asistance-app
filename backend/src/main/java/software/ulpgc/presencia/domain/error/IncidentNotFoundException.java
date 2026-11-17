package software.ulpgc.presencia.domain.error;

public final class IncidentNotFoundException extends DomainException {
    public IncidentNotFoundException(String message) {
        super(ErrorCode.INCIDENT_NOT_FOUND, message);
    }
}
