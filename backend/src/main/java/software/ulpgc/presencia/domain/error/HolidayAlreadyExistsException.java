package software.ulpgc.presencia.domain.error;

public final class HolidayAlreadyExistsException extends DomainException {
    public HolidayAlreadyExistsException(String message) {
        super(ErrorCode.HOLIDAY_ALREADY_EXISTS, message);
    }
}
