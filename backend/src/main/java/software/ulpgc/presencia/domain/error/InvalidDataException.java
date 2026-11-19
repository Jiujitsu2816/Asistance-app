package software.ulpgc.presencia.domain.error;

public final class InvalidDataException extends DomainException {
    public InvalidDataException(String message) {
        super(ErrorCode.INVALID_DATA, message);
    }
}
