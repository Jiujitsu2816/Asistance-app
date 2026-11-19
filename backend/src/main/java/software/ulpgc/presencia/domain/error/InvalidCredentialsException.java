package software.ulpgc.presencia.domain.error;

public final class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
