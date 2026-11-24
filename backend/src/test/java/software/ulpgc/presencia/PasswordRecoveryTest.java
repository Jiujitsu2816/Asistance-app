package software.ulpgc.presencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.presencia.infrastructure.config.AppConfig;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Recuperacion local del acceso cuando se olvida la clave del responsable. */
class PasswordRecoveryTest {
    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private AppConfig config() {
        Properties props = new Properties();
        props.setProperty("presencia.http.port", "0");
        props.setProperty("presencia.db.url", "jdbc:h2:file:" + dir.resolve("db").toAbsolutePath() + ";DB_CLOSE_DELAY=-1");
        props.setProperty("presencia.seed", "false");
        props.setProperty("presencia.security.pbkdf2-iterations", "1000");
        return new AppConfig(Map.of(), props);
    }

    private HttpResponse<String> post(Backend backend, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + backend.port() + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String run(String username, int expectedExit) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = PasswordRecovery.run(config(), username, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(expectedExit, exit, output);
        return output;
    }

    @Test
    void aForgottenAdminPasswordCanBeResetLocally() throws Exception {
        try (Backend backend = new Backend(config(), Clock.systemUTC()).start()) {
            assertEquals(201, post(backend, "/api/auth/setup",
                    "{\"username\":\"jefa\",\"password\":\"clave-olvidada-1\"}").statusCode());
        }

        assertTrue(run(null, 0).contains("jefa"), "lista las cuentas si no se indica usuario");
        assertTrue(run("nadie", 1).contains("No existe el usuario"));
        Matcher matcher = Pattern.compile("Clave nueva para 'jefa': (\\S+)").matcher(run("JEFA", 0));
        assertTrue(matcher.find());
        String newPassword = matcher.group(1);
        assertEquals(16, newPassword.length());

        try (Backend backend = new Backend(config(), Clock.systemUTC()).start()) {
            assertEquals(401, post(backend, "/api/auth/login",
                    "{\"username\":\"jefa\",\"password\":\"clave-olvidada-1\"}").statusCode(), "la clave antigua ya no vale");
            HttpResponse<String> login = post(backend, "/api/auth/login",
                    "{\"username\":\"jefa\",\"password\":\"" + newPassword + "\"}");
            assertEquals(200, login.statusCode());
            String token = json.readTree(login.body()).get("token").asText();
            JsonNode audit = json.readTree(http.send(HttpRequest.newBuilder(
                            URI.create("http://localhost:" + backend.port() + "/api/audit?limit=1"))
                    .header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString()).body());
            assertEquals("PASSWORD_RESET", audit.get(0).get("action").asText());
            assertEquals("recuperacion-local", audit.get(0).get("actor").asText(), "queda auditado");
        }
    }

    @Test
    void withoutAccountsItExplainsThatSetupIsPending() {
        assertTrue(run(null, 0).contains("No hay cuentas"));
    }
}
