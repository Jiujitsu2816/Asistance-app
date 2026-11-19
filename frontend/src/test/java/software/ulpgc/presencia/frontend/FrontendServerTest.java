package software.ulpgc.presencia.frontend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class FrontendServerTest {
    private FrontendServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = new FrontendServer("localhost", 0, "http://api.example:9090/api");
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesTheLoginTheAppAndItsModules() throws Exception {
        for (String app : new String[]{"/", "/app/"}) {
            HttpResponse<String> page = get(app);
            assertEquals(200, page.statusCode(), app);
            assertTrue(page.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            assertTrue(page.body().contains("/config.js"), app + " debe cargar la configuracion");
        }
        assertTrue(get("/shared/api-client.js").body().contains("class HttpPresenceApi"));
        for (String module : new String[]{"fichaje", "consulta", "horarios", "personal"})
            assertEquals(200, get("/modulos/" + module + "/" + module + ".js").statusCode(), module);
        assertTrue(get("/shared/api-client.js").headers().firstValue("Content-Type").orElseThrow()
                .startsWith("text/javascript"));
    }

    @Test
    void publishesTheConfiguredApiUrl() throws Exception {
        assertEquals("window.PRESENCIA_CONFIG = Object.freeze({ apiBaseUrl: \"http://api.example:9090/api\" });\n",
                get("/config.js").body());
    }

    @Test
    void redirectsFoldersAndRejectsUnknownOrUnsafePaths() throws Exception {
        HttpResponse<String> redirect = get("/app");
        assertEquals(301, redirect.statusCode());
        assertEquals("/app/", redirect.headers().firstValue("Location").orElseThrow());
        assertEquals(404, get("/no-existe.html").statusCode());
        assertEquals(400, get("/shared/..%2F..%2Ffrontend.properties").statusCode());
    }
}
