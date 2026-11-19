package software.ulpgc.presencia.domain.error;

public final class ScheduleNotFoundException extends DomainException {
    public ScheduleNotFoundException(String message) {
        super(ErrorCode.SCHEDULE_NOT_FOUND, message);
    }
}
