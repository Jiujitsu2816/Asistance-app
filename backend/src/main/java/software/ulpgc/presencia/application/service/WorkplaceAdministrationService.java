package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.WorkplaceAdministrationUseCase;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.application.port.out.WorkplaceRepository;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.domain.workplace.Workplace;

import java.util.Comparator;
import java.util.List;

public final class WorkplaceAdministrationService implements WorkplaceAdministrationUseCase {
    private final WorkplaceRepository workplaces;
    private final AuditTrail audit;
    private final Transactions transactions;

    public WorkplaceAdministrationService(WorkplaceRepository workplaces, AuditTrail audit, Transactions transactions) {
        this.workplaces = workplaces;
        this.audit = audit;
        this.transactions = transactions;
    }

    @Override
    public Workplace create(Workplace workplace, String actor) {
        return transactions.inTransaction(() -> {
            if (workplaces.findById(workplace.id()).isPresent())
                throw new DomainException(ErrorCode.WORKPLACE_ALREADY_EXISTS, "Ya existe el centro '" + workplace.id() + "'");
            workplaces.save(workplace);
            audit.record(Actors.normalize(actor), "WORKPLACE_CREATED", "workplace", workplace.id(), workplace.name());
            return workplace;
        });
    }

    @Override
    public List<Workplace> list() {
        return workplaces.findAll().stream().sorted(Comparator.comparing(Workplace::name)).toList();
    }
}
