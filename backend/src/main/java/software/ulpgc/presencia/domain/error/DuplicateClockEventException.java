package software.ulpgc.presencia.domain.error;

public final class DuplicateClockEventException extends DomainException {
    public DuplicateClockEventException(String message) {
        super(ErrorCode.DUPLICATE_CLOCK_EVENT, message);
    }
}
