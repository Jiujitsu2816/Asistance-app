package software.ulpgc.presencia.domain.error;

public final class AccountNotFoundException extends DomainException {
    public AccountNotFoundException(String message) {
        super(ErrorCode.ACCOUNT_NOT_FOUND, message);
    }
}
