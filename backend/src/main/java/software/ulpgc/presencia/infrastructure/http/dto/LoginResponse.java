package software.ulpgc.presencia.infrastructure.http.dto;

import java.time.OffsetDateTime;

public record LoginResponse(String token, OffsetDateTime expiresAt, UserDto user) { }
