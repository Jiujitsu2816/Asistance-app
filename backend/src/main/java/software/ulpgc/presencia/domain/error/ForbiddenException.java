package software.ulpgc.presencia.domain.error;

public final class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
