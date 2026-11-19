package software.ulpgc.presencia.domain.error;

public final class WorkplaceAlreadyExistsException extends DomainException {
    public WorkplaceAlreadyExistsException(String message) {
        super(ErrorCode.WORKPLACE_ALREADY_EXISTS, message);
    }
}
