package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;

import java.util.List;

public interface AssignmentRepository {
    void save(ScheduleAssignment assignment);

    List<ScheduleAssignment> findAll();
}
