package software.ulpgc.presencia.domain.error;

public final class InconsistentClockEventException extends DomainException {
    public InconsistentClockEventException(String message) {
        super(ErrorCode.INCONSISTENT_CLOCK_EVENT, message);
    }
}
