package software.ulpgc.presencia.domain.attendance;

/** Estado resumido de un dia. Si hay varias incidencias se muestra la mas grave. */
public enum DayStatus {
    NO_SCHEDULE,
    HOLIDAY,
    REST,
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    EXTRA_WORK,
    EARLY_DEPARTURE,
    LATE,
    CHECKOUT_PENDING,
    ABSENT
}
