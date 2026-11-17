package software.ulpgc.presencia.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * Servidor estatico de las tres aplicaciones frontend (fichaje, consulta y horarios).
 * No accede a la base de datos ni contiene logica de negocio: solo entrega HTML, CSS y JS
 * y publica en /config.js la URL de la API que deben usar.
 */
public final class FrontendServer implements AutoCloseable {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final HttpServer server;
    private final String apiUrl;

    public FrontendServer(String host, int port, String apiUrl) throws IOException {
        this.apiUrl = apiUrl;
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", this::handle);
    }

    public static void main(String[] args) throws IOException {
        Properties defaults = new Properties();
        try (InputStream in = FrontendServer.class.getResourceAsStream("/frontend.properties")) {
            if (in != null) defaults.load(in);
        }
        String host = setting(defaults, "presencia.frontend.host");
        int port = Integer.parseInt(setting(defaults, "presencia.frontend.port"));
        String api = setting(defaults, "presencia.api.url");
        FrontendServer frontend = new FrontendServer(host, port, api);
        frontend.start();
        Runtime.getRuntime().addShutdownHook(new Thread(frontend::close));
        System.out.printf("Frontends en http://%s:%d/ (API: %s)%n", host, frontend.port(), api);
        System.out.printf("  Registro de asistencia: http://%s:%d/fichaje/%n", host, frontend.port());
        System.out.printf("  Consulta de presencia:  http://%s:%d/consulta/%n", host, frontend.port());
        System.out.printf("  Definicion de horarios: http://%s:%d/horarios/%n", host, frontend.port());
    }

    static String setting(Properties defaults, String key) {
        String value = System.getProperty(key);
        if (value == null) value = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        if (value == null) value = defaults.getProperty(key);
        if (value == null) throw new IllegalStateException("Falta la configuracion '" + key + "'");
        return value.strip();
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
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain; charset=utf-8", "Metodo no permitido".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (path.equals("/config.js")) {
                send(exchange, 200, TYPES.get("js"), configScript().getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.contains("..") || path.contains("\\")) {
                send(exchange, 400, "text/plain; charset=utf-8", "Ruta no valida".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            if (!path.endsWith("/") && !lastSegment.contains(".")) {
                // /fichaje -> /fichaje/ para que las rutas relativas de la pagina funcionen
                exchange.getResponseHeaders().set("Location", path + "/");
                send(exchange, 301, "text/plain; charset=utf-8", new byte[0]);
                return;
            }
            if (path.endsWith("/")) path += "index.html";
            byte[] content = resource(path);
            if (content == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String extension = path.substring(path.lastIndexOf('.') + 1);
            send(exchange, 200, TYPES.getOrDefault(extension, "application/octet-stream"), content);
        }
    }

    /** Configuracion en tiempo de ejecucion para el adaptador de comunicacion. */
    String configScript() {
        String escaped = apiUrl.replace("\\", "\\\\").replace("\"", "\\\"");
        return "window.PRESENCIA_CONFIG = Object.freeze({ apiBaseUrl: \"" + escaped + "\" });\n";
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = FrontendServer.class.getResourceAsStream("/web" + path)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length);
        if (!head && body.length > 0) exchange.getResponseBody().write(body);
    }
}
