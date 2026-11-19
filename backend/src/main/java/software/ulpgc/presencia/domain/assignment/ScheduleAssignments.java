package software.ulpgc.presencia.domain.assignment;

import software.ulpgc.presencia.domain.person.Person;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de resolucion: una asignacion individual tiene prioridad sobre la de su equipo.
 * Las asignaciones del mismo destino no se solapan (lo garantiza el caso de uso).
 */
public final class ScheduleAssignments {
    private final Person person;
    private final List<ScheduleAssignment> assignments;

    public ScheduleAssignments(Person person, List<ScheduleAssignment> assignments) {
        this.person = person;
        this.assignments = assignments.stream().filter(a -> a.appliesTo(person)).toList();
    }

    public Optional<ScheduleAssignment> on(LocalDate date) {
        Optional<ScheduleAssignment> own = find(TargetType.PERSON, date);
        return own.isPresent() ? own : find(TargetType.TEAM, date);
    }

    public boolean isEmpty() {
        return assignments.isEmpty();
    }

    public Person person() {
        return person;
    }

    private Optional<ScheduleAssignment> find(TargetType type, LocalDate date) {
        return assignments.stream().filter(a -> a.targetType() == type && a.covers(date)).findFirst();
    }
}
