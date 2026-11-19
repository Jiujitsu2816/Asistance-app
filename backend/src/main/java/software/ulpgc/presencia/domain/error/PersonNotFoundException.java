package software.ulpgc.presencia.domain.error;

public final class PersonNotFoundException extends DomainException {
    public PersonNotFoundException(String message) {
        super(ErrorCode.PERSON_NOT_FOUND, message);
    }
}
