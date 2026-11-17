package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.clocking.ClockEventType;
import software.ulpgc.presencia.domain.clocking.ClockSource;

import java.time.OffsetDateTime;

/** timestamp es opcional: si se omite se usa la hora del servidor. */
public record ClockEventRequest(String personId, ClockEventType type, ClockSource source,
                                OffsetDateTime timestamp, String note) { }
