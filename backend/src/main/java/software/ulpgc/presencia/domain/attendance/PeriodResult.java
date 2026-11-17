package software.ulpgc.presencia.domain.attendance;

import java.time.Instant;

/** Resultado de un periodo esperado: cuando debia empezar/terminar y lo fichado. */
public record PeriodResult(String label, Instant expectedStart, Instant expectedEnd, boolean presenceRequired,
                           Instant firstEntry, Instant lastExit, long workedMinutes, boolean open) { }
