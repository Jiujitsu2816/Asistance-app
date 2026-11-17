package software.ulpgc.presencia.domain.error;

public final class DateOutOfPeriodException extends DomainException {
    public DateOutOfPeriodException(String message) {
        super(ErrorCode.DATE_OUT_OF_PERIOD, message);
    }
}
