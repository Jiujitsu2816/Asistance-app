package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.assignment.TargetType;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.schedule.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** Definicion de horarios, turnos y guardias, su asignacion y el calendario de festivos. */
public interface ScheduleAdministrationUseCase {
    /**
     * @param factory construye el horario con el identificador generado por el sistema.
     */
    Schedule createSchedule(Function<String, Schedule> factory, String actor);

    List<Schedule> listSchedules();

    Schedule getSchedule(String id);

    record AssignScheduleCommand(String scheduleId, TargetType targetType, String targetId,
                                 LocalDate validFrom, LocalDate validTo, String workplaceId, String actor) { }

    ScheduleAssignment assign(AssignScheduleCommand command);

    /** personId null devuelve todas las asignaciones. */
    List<ScheduleAssignment> listAssignments(String personId);

    Holiday addHoliday(Holiday holiday, String actor);

    List<Holiday> listHolidays();
}
