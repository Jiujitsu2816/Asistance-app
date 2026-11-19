package software.ulpgc.presencia.application.security;

import java.time.Instant;

/** Sesion iniciada: token opaco aleatorio, usuario y caducidad. */
public record Session(String token, String username, Instant expiresAt) { }
