package software.ulpgc.presencia.domain.schedule;

/** Tipos de horario soportados. Cada uno tiene su propia clase con sus reglas. */
public enum ScheduleKind {
    /** Jornada continua de oficina: un unico tramo los dias laborables. */
    CONTINUOUS,
    /** Horario partido de tienda o restaurante: dos o mas tramos separados por una pausa. */
    SPLIT,
    /** Turnos rotatorios: un ciclo de N dias que se repite desde una fecha de referencia. */
    ROTATING_SHIFT,
    /** Guardias en fechas concretas, de presencia fisica o localizadas. */
    ON_CALL
}
