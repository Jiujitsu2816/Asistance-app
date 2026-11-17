package software.ulpgc.presencia.infrastructure.http.dto;

import java.time.LocalDate;

/** Se indica personId o team (exactamente uno). validTo es opcional. */
public record AssignmentRequest(String scheduleId, String personId, String team, LocalDate validFrom,
                                LocalDate validTo) { }
