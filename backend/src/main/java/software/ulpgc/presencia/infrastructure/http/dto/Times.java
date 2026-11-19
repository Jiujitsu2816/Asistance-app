package software.ulpgc.presencia.infrastructure.http.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Los instantes se devuelven como fecha-hora con desplazamiento en la zona configurada. */
public record Times(ZoneId zone) {
    public OffsetDateTime of(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toOffsetDateTime();
    }
}
