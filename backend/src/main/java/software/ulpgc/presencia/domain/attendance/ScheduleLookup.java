package software.ulpgc.presencia.domain.attendance;

import software.ulpgc.presencia.domain.schedule.Schedule;

import java.time.LocalDate;
import java.util.Optional;

/** Horario vigente de una persona en una fecha (ya resuelta la asignacion). */
@FunctionalInterface
public interface ScheduleLookup {
    Optional<Schedule> on(LocalDate date);
}
