package software.ulpgc.presencia.domain.error;

public final class AccountAlreadyExistsException extends DomainException {
    public AccountAlreadyExistsException(String message) {
        super(ErrorCode.ACCOUNT_ALREADY_EXISTS, message);
    }
}
