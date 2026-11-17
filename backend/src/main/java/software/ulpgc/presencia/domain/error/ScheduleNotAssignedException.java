package software.ulpgc.presencia.domain.error;

public final class ScheduleNotAssignedException extends DomainException {
    public ScheduleNotAssignedException(String message) {
        super(ErrorCode.SCHEDULE_NOT_ASSIGNED, message);
    }
}
