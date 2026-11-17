package software.ulpgc.presencia.infrastructure.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import software.ulpgc.presencia.domain.Require;
import software.ulpgc.presencia.domain.schedule.ContinuousSchedule;
import software.ulpgc.presencia.domain.schedule.CycleDay;
import software.ulpgc.presencia.domain.schedule.OnCallMode;
import software.ulpgc.presencia.domain.schedule.OnCallSchedule;
import software.ulpgc.presencia.domain.schedule.RotatingShiftSchedule;
import software.ulpgc.presencia.domain.schedule.Schedule;
import software.ulpgc.presencia.domain.schedule.ScheduleKind;
import software.ulpgc.presencia.domain.schedule.Shift;
import software.ulpgc.presencia.domain.schedule.SplitSchedule;
import software.ulpgc.presencia.domain.schedule.TimeSlot;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Representacion JSON de un horario. Es el cuerpo de POST /api/schedules, la respuesta de
 * GET /api/schedules y el formato con el que el adaptador de persistencia guarda la
 * definicion (columna schedule.definition). Solo se usan los campos del tipo indicado.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(value = {"summary"}, allowGetters = true)
public record ScheduleDocument(String id, String name, ScheduleKind kind, Integer toleranceMinutes,
                               List<DayOfWeek> workDays, List<Slot> slots,
                               LocalDate cycleStart, List<CycleEntry> cycle,
                               LocalTime start, Integer durationMinutes, OnCallMode mode, List<LocalDate> dates,
                               String summary) {

    public record Slot(LocalTime start, LocalTime end) { }

    /** Dia de ciclo: {"rest": true} o {"label": "Manana", "start": "07:00", "durationMinutes": 480}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CycleEntry(Boolean rest, String label, LocalTime start, Integer durationMinutes) { }

    public Schedule toDomain(String scheduleId) {
        Require.present(kind, "kind");
        Duration tolerance = Duration.ofMinutes(toleranceMinutes == null ? 0 : toleranceMinutes);
        return switch (kind) {
            case CONTINUOUS -> {
                List<TimeSlot> parsed = parseSlots();
                Require.that(parsed.size() == 1, "Un horario corrido tiene exactamente un tramo");
                yield new ContinuousSchedule(scheduleId, name, tolerance, parseDays(), parsed.getFirst());
            }
            case SPLIT -> new SplitSchedule(scheduleId, name, tolerance, parseDays(), parseSlots());
            case ROTATING_SHIFT -> new RotatingShiftSchedule(scheduleId, name, tolerance, cycleStart, parseCycle());
            case ON_CALL -> {
                Require.present(durationMinutes, "durationMinutes");
                Require.present(dates, "dates");
                yield new OnCallSchedule(scheduleId, name, tolerance, start, Duration.ofMinutes(durationMinutes),
                        mode, new TreeSet<>(dates.stream().filter(Objects::nonNull).toList()));
            }
        };
    }

    private EnumSet<DayOfWeek> parseDays() {
        Require.present(workDays, "workDays");
        Require.that(!workDays.isEmpty() && workDays.stream().allMatch(Objects::nonNull),
                "Indica al menos un dia laborable");
        return EnumSet.copyOf(workDays);
    }

    private List<TimeSlot> parseSlots() {
        Require.present(slots, "slots");
        return slots.stream().map(s -> {
            Require.present(s, "slots");
            return new TimeSlot(s.start(), s.end());
        }).toList();
    }

    private List<CycleDay> parseCycle() {
        Require.present(cycle, "cycle");
        return cycle.stream().map(entry -> {
            Require.present(entry, "cycle");
            if (Boolean.TRUE.equals(entry.rest())) return (CycleDay) new CycleDay.Rest();
            Require.present(entry.durationMinutes(), "cycle.durationMinutes");
            return new CycleDay.Work(new Shift(entry.label(), entry.start(),
                    Duration.ofMinutes(entry.durationMinutes())));
        }).toList();
    }

    public static ScheduleDocument from(Schedule schedule) {
        int tolerance = (int) schedule.tolerance().toMinutes();
        return switch (schedule) {
            case ContinuousSchedule s -> new ScheduleDocument(s.id(), s.name(), s.kind(), tolerance,
                    sorted(s.workDays()), List.of(slot(s.slot())), null, null, null, null, null, null, s.summary());
            case SplitSchedule s -> new ScheduleDocument(s.id(), s.name(), s.kind(), tolerance,
                    sorted(s.workDays()), s.slots().stream().map(ScheduleDocument::slot).toList(),
                    null, null, null, null, null, null, s.summary());
            case RotatingShiftSchedule s -> new ScheduleDocument(s.id(), s.name(), s.kind(), tolerance, null, null,
                    s.cycleStart(), s.cycle().stream().map(ScheduleDocument::entry).toList(),
                    null, null, null, null, s.summary());
            case OnCallSchedule s -> new ScheduleDocument(s.id(), s.name(), s.kind(), tolerance, null, null,
                    null, null, s.start(), (int) s.duration().toMinutes(), s.mode(), List.copyOf(s.dates()),
                    s.summary());
        };
    }

    private static List<DayOfWeek> sorted(java.util.Set<DayOfWeek> days) {
        return List.copyOf(EnumSet.copyOf(days));
    }

    private static Slot slot(TimeSlot slot) {
        return new Slot(slot.start(), slot.end());
    }

    private static CycleEntry entry(CycleDay day) {
        return switch (day) {
            case CycleDay.Work(Shift shift) -> new CycleEntry(null, shift.label(), shift.start(),
                    (int) shift.duration().toMinutes());
            case CycleDay.Rest rest -> new CycleEntry(true, null, null, null);
        };
    }
}
