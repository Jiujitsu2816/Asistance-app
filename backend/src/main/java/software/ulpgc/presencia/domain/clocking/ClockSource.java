package software.ulpgc.presencia.domain.clocking;

/** Origen del fichaje, guardado para la trazabilidad. */
public enum ClockSource {
    /** Terminal de fichaje (aplicacion de registro de asistencia). */
    KIOSK,
    /** Aplicacion web o movil personal. */
    WEB,
    /** Registro manual realizado por administracion (p. ej. olvido de fichaje). */
    ADMIN,
    /** Carga de datos iniciales o importacion. */
    IMPORT
}
