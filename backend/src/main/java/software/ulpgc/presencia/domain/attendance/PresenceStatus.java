package software.ulpgc.presencia.domain.attendance;

/** Estado de presencia en este momento. */
public enum PresenceStatus {
    /** Dentro (entrada abierta) y en horario. */
    PRESENT,
    /** Dentro fuera de cualquier periodo esperado (horas extra, adelanto...). */
    PRESENT_OUT_OF_SCHEDULE,
    /** Dentro cuando su periodo ya termino: salida pendiente. */
    CHECKOUT_PENDING,
    /** Deberia estar dentro y no lo esta (pasada la tolerancia). */
    ABSENT,
    /** Su periodo acaba de empezar y aun esta dentro de la tolerancia. */
    EXPECTED,
    /** En guardia localizada: no se exige presencia. */
    ON_CALL,
    /** Fuera y sin periodo en curso. */
    OFF_DUTY,
    /** Sin horario asignado hoy. */
    NO_SCHEDULE
}
