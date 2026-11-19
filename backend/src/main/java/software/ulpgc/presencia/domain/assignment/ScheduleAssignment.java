package software.ulpgc.presencia.domain.assignment;

import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.person.Person;

import java.time.LocalDate;

/**
 * Asignacion de un horario a una persona o a un equipo durante un periodo, en un centro de trabajo.
 *
 * @param validTo     ultimo dia incluido; null indica una asignacion sin fecha de fin.
 * @param workplaceId centro de trabajo donde se cumple el horario (opcional).
 */
public record ScheduleAssignment(String id, String scheduleId, TargetType targetType, String targetId,
                                 LocalDate validFrom, LocalDate validTo, String workplaceId) {
    public ScheduleAssignment {
        id = Require.text(id, "id", 64);
        scheduleId = Require.text(scheduleId, "scheduleId", 64);
        Require.present(targetType, "targetType");
        targetId = Require.text(targetId, targetType == TargetType.PERSON ? "personId" : "team", 40);
        Require.present(validFrom, "validFrom");
        Require.that(validTo == null || !validTo.isBefore(validFrom),
                "La fecha de fin (" + validTo + ") no puede ser anterior a la de inicio (" + validFrom + ")");
        workplaceId = Require.optionalText(workplaceId, "workplaceId", 32);
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }

    public boolean overlaps(ScheduleAssignment other) {
        boolean thisEndsBefore = validTo != null && validTo.isBefore(other.validFrom);
        boolean otherEndsBefore = other.validTo != null && other.validTo.isBefore(validFrom);
        return !thisEndsBefore && !otherEndsBefore;
    }

    public boolean sameTarget(ScheduleAssignment other) {
        return targetType == other.targetType && targetId.equalsIgnoreCase(other.targetId);
    }

    public boolean appliesTo(Person person) {
        return switch (targetType) {
            case PERSON -> targetId.equals(person.id());
            case TEAM -> person.belongsTo(targetId);
        };
    }
}
