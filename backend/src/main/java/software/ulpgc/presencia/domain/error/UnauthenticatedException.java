package software.ulpgc.presencia.domain.error;

public final class UnauthenticatedException extends DomainException {
    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
