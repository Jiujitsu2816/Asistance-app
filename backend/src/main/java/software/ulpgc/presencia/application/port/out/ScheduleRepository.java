package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.schedule.Schedule;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {
    void save(Schedule schedule);

    Optional<Schedule> findById(String id);

    List<Schedule> findAll();
}
