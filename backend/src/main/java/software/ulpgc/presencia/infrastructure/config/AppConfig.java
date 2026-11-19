package software.ulpgc.presencia.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Map;
import java.util.Properties;

/**
 * Configuracion: application.properties del classpath, sobrescrita por variables de entorno
 * y, con mayor prioridad, por propiedades del sistema.
 */
public final class AppConfig {
    private final Properties defaults = new Properties();
    private final Map<String, String> env;
    private final Properties system;

    public AppConfig() {
        this(System.getenv(), System.getProperties());
    }

    public AppConfig(Map<String, String> env, Properties system) {
        this.env = env;
        this.system = system;
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) defaults.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer application.properties", e);
        }
    }

    public String get(String key) {
        String value = system.getProperty(key);
        if (value == null) value = env.get(key.toUpperCase().replace('.', '_').replace('-', '_'));
        if (value == null) value = defaults.getProperty(key);
        if (value == null) throw new IllegalStateException("Falta la configuracion '" + key + "'");
        return value.strip();
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("La configuracion '" + key + "' debe ser un numero", e);
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public String host() { return get("presencia.http.host"); }

    public int port() { return getInt("presencia.http.port"); }

    public String allowedOrigin() { return get("presencia.cors.allowed-origin"); }

    public String dbUrl() { return get("presencia.db.url"); }

    public String dbUser() { return get("presencia.db.user"); }

    public String dbPassword() { return get("presencia.db.password"); }

    public ZoneId zone() { return ZoneId.of(get("presencia.zone")); }

    public boolean seed() { return getBoolean("presencia.seed"); }

    public java.time.Duration sessionDuration() {
        return java.time.Duration.ofMinutes(getInt("presencia.security.session-minutes"));
    }

    public int pbkdf2Iterations() { return getInt("presencia.security.pbkdf2-iterations"); }
}
