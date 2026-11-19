package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.workplace.Workplace;

import java.util.List;
import java.util.Optional;

public interface WorkplaceRepository {
    void save(Workplace workplace);

    Optional<Workplace> findById(String id);

    List<Workplace> findAll();
}
