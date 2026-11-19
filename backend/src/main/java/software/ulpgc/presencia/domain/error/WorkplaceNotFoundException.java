package software.ulpgc.presencia.domain.error;

public final class WorkplaceNotFoundException extends DomainException {
    public WorkplaceNotFoundException(String message) {
        super(ErrorCode.WORKPLACE_NOT_FOUND, message);
    }
}
