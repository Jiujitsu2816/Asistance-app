package software.ulpgc.presencia.domain.error;

public final class IncidentAlreadyJustifiedException extends DomainException {
    public IncidentAlreadyJustifiedException(String message) {
        super(ErrorCode.INCIDENT_ALREADY_JUSTIFIED, message);
    }
}
