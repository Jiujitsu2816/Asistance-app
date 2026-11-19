package software.ulpgc.presencia.infrastructure.http.dto;

/** Cuerpo uniforme de error: codigo estable, mensaje legible y estado HTTP. */
public record ErrorDto(String error, String message, int status) { }
