package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.workplace.Workplace;

public record WorkplaceDto(String id, String name, String address) {
    public static WorkplaceDto from(Workplace w) {
        return w == null ? null : new WorkplaceDto(w.id(), w.name(), w.address());
    }
}
