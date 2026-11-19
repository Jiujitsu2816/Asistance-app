package software.ulpgc.presencia.domain.error;

public final class PersonAlreadyExistsException extends DomainException {
    public PersonAlreadyExistsException(String message) {
        super(ErrorCode.PERSON_ALREADY_EXISTS, message);
    }
}
