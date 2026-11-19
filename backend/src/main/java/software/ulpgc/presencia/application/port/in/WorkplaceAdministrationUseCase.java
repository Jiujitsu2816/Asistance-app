package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.workplace.Workplace;

import java.util.List;

/** Centros de trabajo. */
public interface WorkplaceAdministrationUseCase {
    Workplace create(Workplace workplace, String actor);

    List<Workplace> list();
}
