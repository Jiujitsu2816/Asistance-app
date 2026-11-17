package software.ulpgc.presencia.domain.schedule;

import software.ulpgc.presencia.domain.Require;

/** Dia de un ciclo de turnos rotatorios: trabajo con un turno o descanso. */
public sealed interface CycleDay {
    record Work(Shift shift) implements CycleDay {
        public Work {
            Require.present(shift, "shift");
        }
    }

    record Rest() implements CycleDay { }
}
