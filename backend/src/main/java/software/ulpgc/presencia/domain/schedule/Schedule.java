package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Horario. Cada tipo implementa de forma explicita sus reglas en su propia clase, en lugar
 * de repartir condicionales por la aplicacion. Para anadir un tipo nuevo basta con una
 * nueva implementacion y su traduccion en los adaptadores.
 */
public sealed interface Schedule
        permits ContinuousSchedule, SplitSchedule, RotatingShiftSchedule, OnCallSchedule {

    String id();

    String name();

    ScheduleKind kind();

    /** Margen de cortesia para retrasos, salidas anticipadas y salidas pendientes. */
    Duration tolerance();

    /** Periodos de trabajo que comienzan en la fecha indicada. */
    List<WorkPeriod> periodsStartingOn(LocalDate date);

    /**
     * Si es true, un festivo del calendario anula los periodos de ese dia. Las oficinas y
     * tiendas cierran en festivo; los turnos y guardias mantienen la cobertura continua.
     */
    boolean exemptOnHolidays();

    /** Descripcion breve para listados. */
    String summary();

    static Duration validTolerance(Duration tolerance) {
        Require.present(tolerance, "toleranceMinutes");
        Require.that(!tolerance.isNegative() && tolerance.toMinutes() <= 120,
                "La tolerancia debe estar entre 0 y 120 minutos");
        return tolerance;
    }
}
