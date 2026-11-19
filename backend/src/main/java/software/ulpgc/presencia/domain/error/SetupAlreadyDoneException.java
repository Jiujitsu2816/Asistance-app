package software.ulpgc.presencia.domain.error;

public final class SetupAlreadyDoneException extends DomainException {
    public SetupAlreadyDoneException(String message) {
        super(ErrorCode.SETUP_ALREADY_DONE, message);
    }
}
