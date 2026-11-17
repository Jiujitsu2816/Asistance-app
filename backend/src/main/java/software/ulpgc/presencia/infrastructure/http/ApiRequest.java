package software.ulpgc.presencia.infrastructure.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.presencia.domain.error.InvalidDataException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Peticion HTTP ya enrutada: parametros de ruta, de consulta, cabeceras y cuerpo. */
public final class ApiRequest {
    private final Map<String, String> pathParams;
    private final Map<String, String> query;
    private final byte[] body;
    private final String actor;
    private final ObjectMapper json;

    ApiRequest(Map<String, String> pathParams, String rawQuery, byte[] body, String actor, ObjectMapper json) {
        this.pathParams = pathParams;
        this.query = parseQuery(rawQuery);
        this.body = body;
        this.actor = actor;
        this.json = json;
    }

    public String path(String name) {
        return pathParams.get(name);
    }

    public String query(String name) {
        String value = query.get(name);
        return value == null || value.isBlank() ? null : value.strip();
    }

    public LocalDate queryDate(String name) {
        String value = query(name);
        if (value == null) throw new InvalidDataException("Falta el parametro '" + name + "' (formato AAAA-MM-DD)");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidDataException("El parametro '" + name + "' no es una fecha AAAA-MM-DD: " + value);
        }
    }

    public LocalDate queryDate(String name, LocalDate fallback) {
        return query(name) == null ? fallback : queryDate(name);
    }

    public <E extends Enum<E>> E queryEnum(String name, Class<E> type) {
        String value = query(name);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidDataException("Valor no valido para '" + name + "': " + value);
        }
    }

    public int queryInt(String name, int fallback) {
        String value = query(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidDataException("El parametro '" + name + "' debe ser un numero");
        }
    }

    /** Cuerpo JSON obligatorio. Los errores de formato se traducen en INVALID_DATA. */
    public <T> T body(Class<T> type) throws IOException {
        if (body.length == 0) throw new InvalidDataException("La peticion necesita un cuerpo JSON");
        T value = json.readValue(body, type);
        if (value == null) throw new InvalidDataException("La peticion necesita un cuerpo JSON");
        return value;
    }

    /** Valor de la cabecera X-Actor: quien realiza la operacion (para la auditoria). */
    public String actor() {
        return actor;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.putIfAbsent(key, value);
        }
        return result;
    }
}
