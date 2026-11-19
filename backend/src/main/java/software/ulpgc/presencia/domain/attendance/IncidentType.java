package software.ulpgc.presencia.domain.attendance;

public enum IncidentType {
    /** No hay ninguna entrada para un periodo de presencia obligatoria. */
    ABSENCE,
    /** Primera entrada del periodo posterior al inicio mas la tolerancia. */
    LATE_ARRIVAL,
    /** Ultima salida del periodo anterior al fin menos la tolerancia. */
    EARLY_DEPARTURE,
    /** Entrada abierta cuando el periodo ya termino (mas la tolerancia). */
    MISSING_CHECKOUT,
    /** Trabajo fichado fuera de cualquier periodo esperado (informativo). */
    UNSCHEDULED_WORK
}
