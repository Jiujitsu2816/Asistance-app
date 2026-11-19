package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.TargetType;

import java.time.LocalDate;

public record AssignmentDto(String id, String scheduleId, TargetType targetType, String targetId,
                            LocalDate validFrom, LocalDate validTo, String workplaceId) {
    public static AssignmentDto from(ScheduleAssignment a) {
        return new AssignmentDto(a.id(), a.scheduleId(), a.targetType(), a.targetId(), a.validFrom(), a.validTo(),
                a.workplaceId());
    }
}
