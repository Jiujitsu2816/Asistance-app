package software.ulpgc.presencia.infrastructure.http.dto;

/** Respuesta al fichar: el fichaje registrado y el estado de presencia resultante. */
public record ClockEventResponse(ClockEventDto event, PresenceDto presence) { }
