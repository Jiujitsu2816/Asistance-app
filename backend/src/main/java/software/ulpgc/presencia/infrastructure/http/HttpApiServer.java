package software.ulpgc.presencia.infrastructure.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.infrastructure.http.dto.ErrorDto;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Adaptador de entrada HTTP. Traduce peticiones JSON a llamadas a los casos de uso y los
 * errores de dominio a respuestas con un cuerpo de error uniforme.
 */
public final class HttpApiServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(HttpApiServer.class.getName());
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final HttpServer server;
    private final ExecutorService executor;
    private final Router router;
    private final ObjectMapper json;
    private final String allowedOrigin;

    public HttpApiServer(String host, int port, Router router, ObjectMapper json, String allowedOrigin)
            throws IOException {
        this.router = router;
        this.json = json;
        this.allowedOrigin = allowedOrigin;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.executor = Executors.newFixedThreadPool(8);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
            exchange.getResponseHeaders().add("Vary", "Origin");
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if ("OPTIONS".equals(method)) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Actor");
                exchange.getResponseHeaders().add("Access-Control-Max-Age", "600");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            ApiResponse response;
            try {
                response = dispatch(exchange, method, path);
            } catch (DomainException e) {
                response = error(statusOf(e.code()), e.code().name(), e.getMessage());
            } catch (JsonProcessingException e) {
                response = error(400, ErrorCode.INVALID_DATA.name(), describe(e));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error no controlado en " + method + " " + path, e);
                response = error(500, "INTERNAL_ERROR", "Error interno del servidor");
            }
            write(exchange, response);
        }
    }

    private ApiResponse dispatch(HttpExchange exchange, String method, String rawPath) throws Exception {
        String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        var match = router.find(method, path);
        if (match.isEmpty()) {
            return router.pathExists(path)
                    ? error(405, "METHOD_NOT_ALLOWED", "Metodo " + method + " no permitido en " + path)
                    : error(404, "NOT_FOUND", "No existe la ruta " + path);
        }
        if ("POST".equals(method)) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("application/json"))
                return error(415, "UNSUPPORTED_MEDIA_TYPE", "El cuerpo debe enviarse como application/json");
        }
        byte[] body = readBody(exchange.getRequestBody());
        if (body == null) return error(413, "PAYLOAD_TOO_LARGE", "El cuerpo supera " + MAX_BODY_BYTES + " bytes");
        ApiRequest request = new ApiRequest(match.get().pathParams(), exchange.getRequestURI().getRawQuery(), body,
                exchange.getRequestHeaders().getFirst("X-Actor"), json);
        return match.get().handler().handle(request);
    }

    private static byte[] readBody(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
        return data.length > MAX_BODY_BYTES ? null : data;
    }

    private void write(HttpExchange exchange, ApiResponse response) throws IOException {
        byte[] bytes = response.body() == null ? new byte[0] : json.writeValueAsBytes(response.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }

    private static ApiResponse error(int status, String code, String message) {
        return new ApiResponse(status, new ErrorDto(code, message, status));
    }

    static int statusOf(ErrorCode code) {
        return switch (code) {
            case INVALID_DATA -> 400;
            case PERSON_NOT_FOUND, SCHEDULE_NOT_FOUND, INCIDENT_NOT_FOUND -> 404;
            case PERSON_ALREADY_EXISTS, HOLIDAY_ALREADY_EXISTS, ASSIGNMENT_OVERLAP, DUPLICATE_CLOCK_EVENT,
                 INCONSISTENT_CLOCK_EVENT, INCIDENT_ALREADY_JUSTIFIED -> 409;
            case SCHEDULE_NOT_ASSIGNED, DATE_OUT_OF_PERIOD -> 422;
        };
    }

    private static String describe(JsonProcessingException e) {
        if (e instanceof UnrecognizedPropertyException u)
            return "Campo desconocido '" + u.getPropertyName() + "'";
        if (e instanceof InvalidFormatException f)
            return "Valor no valido para '" + fieldPath(f) + "': " + f.getValue();
        if (e instanceof JsonMappingException m && !m.getPath().isEmpty())
            return "Valor no valido para '" + fieldPath(m) + "'";
        return "JSON mal formado";
    }

    private static String fieldPath(JsonMappingException e) {
        return e.getPath().stream()
                .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
