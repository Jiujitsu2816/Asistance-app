package software.ulpgc.presencia.frontend.api;

import java.net.URI;

/**
 * URL base de la API. Se toma de la propiedad del sistema presencia.api.url, de la variable
 * de entorno PRESENCIA_API_URL o, por defecto, http://localhost:8080/api.
 */
public final class ApiConfig {
    public static final String DEFAULT_URL = "http://localhost:8080/api";

    private ApiConfig() { }

    public static URI baseUrl() {
        String value = System.getProperty("presencia.api.url");
        if (value == null || value.isBlank()) value = System.getenv("PRESENCIA_API_URL");
        if (value == null || value.isBlank()) value = DEFAULT_URL;
        return URI.create(value.strip().replaceAll("/+$", ""));
    }
}
