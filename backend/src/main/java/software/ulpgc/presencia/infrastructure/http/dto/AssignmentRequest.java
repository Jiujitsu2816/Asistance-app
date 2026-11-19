package software.ulpgc.presencia.infrastructure.http.dto;

import java.time.LocalDate;

/** Se indica personId o team (exactamente uno). validTo y workplaceId son opcionales. */
public record AssignmentRequest(String scheduleId, String personId, String team, LocalDate validFrom,
                                LocalDate validTo, String workplaceId) { }
