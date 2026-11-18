package software.ulpgc.presencia.frontend.api;

import software.ulpgc.presencia.Backend;
import software.ulpgc.presencia.infrastructure.config.AppConfig;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

/**
 * Backend real (HTTP + H2 en un directorio temporal) con datos de ejemplo y un reloj fijo,
 * para probar los frontends de extremo a extremo de forma reproducible.
 */
public final class TestBackend implements AutoCloseable {
    /** Martes 17/11/2026 a las 11:00 en Canarias (UTC+0 en noviembre). */
    public static final Instant NOW = Instant.parse("2026-11-17T11:00:00Z");

    private final Backend backend;

    public TestBackend(Path dir) throws Exception {
        Properties props = new Properties();
        props.setProperty("presencia.http.port", "0");
        props.setProperty("presencia.db.url", "jdbc:h2:file:" + dir.resolve("db").toAbsolutePath() + ";DB_CLOSE_DELAY=-1");
        props.setProperty("presencia.seed", "true");
        backend = new Backend(new AppConfig(Map.of(), props), Clock.fixed(NOW, ZoneId.of("Atlantic/Canary"))).start();
    }

    public URI url() {
        return URI.create("http://localhost:" + backend.port() + "/api");
    }

    public PresenceApi api(String actor) {
        return new HttpPresenceApi(url(), actor);
    }

    @Override
    public void close() {
        backend.close();
    }
}
