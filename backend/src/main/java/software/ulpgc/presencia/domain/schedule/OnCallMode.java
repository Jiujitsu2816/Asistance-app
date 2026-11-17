package software.ulpgc.presencia.domain.schedule;

public enum OnCallMode {
    /** Guardia de presencia fisica: se exige fichar como en un turno. */
    PHYSICAL,
    /** Guardia localizada: no se exige presencia; si se acude, el tiempo fichado computa. */
    LOCALIZED
}
