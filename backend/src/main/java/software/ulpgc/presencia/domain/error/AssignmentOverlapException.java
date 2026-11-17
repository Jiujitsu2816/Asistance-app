package software.ulpgc.presencia.domain.error;

public final class AssignmentOverlapException extends DomainException {
    public AssignmentOverlapException(String message) {
        super(ErrorCode.ASSIGNMENT_OVERLAP, message);
    }
}
