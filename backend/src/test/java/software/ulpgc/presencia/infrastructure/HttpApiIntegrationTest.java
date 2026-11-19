package software.ulpgc.presencia.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.presencia.Backend;
import software.ulpgc.presencia.infrastructure.config.AppConfig;
import software.ulpgc.presencia.support.MutableClock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;

/**
 * Pruebas de extremo a extremo del backend real: HTTP + casos de uso + H2 en un fichero
 * temporal. Verifican el contrato (codigos de estado y cuerpos) y la persistencia.
 */
class HttpApiIntegrationTest {
    @TempDir
    Path dir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(at(MONDAY, 7, 30), ZONE);
    private Backend backend;
    /** Clave de una cuenta creada solo dentro de la prueba (base de datos temporal). */
    private static final String ADMIN_PASSWORD = "clave-de-prueba-1";
    private String token;

    private Backend start(boolean seed) throws Exception {
        Properties props = new Properties();
        props.setProperty("presencia.http.port", "0");
        props.setProperty("presencia.db.url", "jdbc:h2:file:" + dir.resolve("db").toAbsolutePath() + ";DB_CLOSE_DELAY=-1");
        props.setProperty("presencia.seed", Boolean.toString(seed));
        props.setProperty("presencia.security.pbkdf2-iterations", "1000");
        Backend started = new Backend(new AppConfig(Map.of(), props), clock).start();
        backend = started;
        token = null;
        // La primera vez se crea el responsable; tras un reinicio se vuelve a iniciar sesion.
        Reply status = get("/api/auth/status");
        Reply login = post(status.body().get("setupRequired").asBoolean() ? "/api/auth/setup" : "/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}");
        token = login.body().get("token").asText();
        return started;
    }

    @BeforeEach
    void setUp() throws Exception {
        backend = start(false);
    }

    @AfterEach
    void tearDown() {
        if (backend != null) backend.close();
    }

    private record Reply(int status, JsonNode body) { }

    private Reply get(String path) throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path))).GET().build());
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private Reply post(String path, String body) throws Exception {
        return send(authorized(HttpRequest.newBuilder(uri(path))).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private Reply send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isEmpty() ? null : json.readTree(response.body()));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + backend.port() + path);
    }

    private String createOffice() throws Exception {
        Reply r = post("/api/schedules", """
                {"name":"Oficina","kind":"CONTINUOUS","toleranceMinutes":10,
                 "workDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY"],
                 "slots":[{"start":"08:00","end":"15:00"}]}""");
        assertEquals(201, r.status(), r.body().toString());
        return r.body().get("id").asText();
    }

    @Test
    void clockInAndOutAndQueryTheDay() throws Exception {
        assertEquals(201, post("/api/people", "{\"id\":\"EMP-1\",\"displayName\":\"Persona Uno\"}").status());
        String office = createOffice();
        assertEquals(201, post("/api/assignments", "{\"scheduleId\":\"" + office
                + "\",\"personId\":\"EMP-1\",\"validFrom\":\"2026-11-01\"}").status());

        clock.set(at(MONDAY, 8, 25));
        Reply in = post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}");
        assertEquals(201, in.status());
        assertEquals("PRESENT", in.body().at("/presence/status").asText());
        assertEquals("LATE", in.body().at("/presence/todayStatus").asText());

        clock.set(at(MONDAY, 15, 0));
        assertEquals(201, post("/api/clock-events",
                "{\"personId\":\"EMP-1\",\"type\":\"EXIT\",\"source\":\"WEB\",\"note\":\"fin\"}").status());

        clock.set(at(MONDAY, 18, 0));
        Reply report = get("/api/reports/daily?personId=EMP-1&from=2026-11-16&to=2026-11-16");
        assertEquals(200, report.status());
        assertEquals(395, report.body().get("workedMinutes").asInt());
        assertEquals(-25, report.body().get("deviationMinutes").asInt());
        assertEquals("LATE_ARRIVAL", report.body().at("/days/0/incidents/0/type").asText());

        Reply events = get("/api/clock-events?personId=EMP-1&from=2026-11-16&to=2026-11-16");
        assertEquals(2, events.body().size());
        assertEquals("2026-11-16T08:25:00Z", events.body().get(0).get("occurredAt").asText());

        Reply incidents = get("/api/incidents?from=2026-11-16&to=2026-11-16&type=LATE_ARRIVAL");
        String key = incidents.body().get(0).get("key").asText();
        assertEquals(201, post("/api/incidents/justifications",
                "{\"incidentKey\":\"" + key + "\",\"reason\":\"Atasco justificado\"}").status());
        assertTrue(get("/api/incidents?from=2026-11-16&to=2026-11-16").body().get(0).get("justified").asBoolean());
        assertEquals("admin", get("/api/audit?limit=1").body().get(0).get("actor").asText());
    }

    @Test
    void contractErrors() throws Exception {
        post("/api/people", "{\"id\":\"EMP-1\",\"displayName\":\"Persona Uno\"}");
        assertError(post("/api/clock-events", "{\"personId\":\"X\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}"),
                404, "PERSON_NOT_FOUND");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}"),
                422, "SCHEDULE_NOT_ASSIGNED");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"EXIT\",\"source\":\"KIOSK\"}"),
                409, "INCONSISTENT_CLOCK_EVENT");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\"}"), 400, "INVALID_DATA");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"OTRO\"}"), 400, "INVALID_DATA");
        assertError(post("/api/clock-events", "no es json"), 400, "INVALID_DATA");
        assertError(post("/api/people", "{\"id\":\"EMP-1\",\"displayName\":\"Repetida\"}"), 409, "PERSON_ALREADY_EXISTS");
        assertError(post("/api/people", "{\"id\":\"con espacios\",\"displayName\":\"X\"}"), 400, "INVALID_DATA");
        assertError(post("/api/schedules", "{\"name\":\"Partido\",\"kind\":\"SPLIT\",\"workDays\":[\"MONDAY\"],"
                + "\"slots\":[{\"start\":\"09:00\",\"end\":\"14:00\"}]}"), 400, "INVALID_DATA");
        assertError(get("/api/schedules/NOPE"), 404, "SCHEDULE_NOT_FOUND");
        assertError(get("/api/reports/daily?personId=EMP-1&from=2026-11-20&to=2026-11-01"), 400, "INVALID_DATA");
        assertError(get("/api/no-existe"), 404, "NOT_FOUND");

        String office = createOffice();
        post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"EMP-1\","
                + "\"validFrom\":\"2026-11-01\",\"validTo\":\"2026-11-10\"}");
        assertError(post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"EMP-1\","
                + "\"validFrom\":\"2026-11-05\"}"), 409, "ASSIGNMENT_OVERLAP");
        assertError(post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"EMP-1\","
                + "\"team\":\"x\",\"validFrom\":\"2026-11-05\"}"), 400, "INVALID_DATA");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}"),
                422, "DATE_OUT_OF_PERIOD");

        post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"EMP-1\",\"validFrom\":\"2026-11-11\"}");
        clock.set(at(MONDAY, 8, 0));
        assertEquals(201, post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}").status());
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}"),
                409, "DUPLICATE_CLOCK_EVENT");
    }

    private static void assertError(Reply reply, int status, String code) {
        assertEquals(status, reply.status(), String.valueOf(reply.body()));
        assertEquals(code, reply.body().get("error").asText());
        assertEquals(status, reply.body().get("status").asInt());
        assertFalse(reply.body().get("message").asText().isBlank());
    }

    @Test
    void allScheduleKindsRoundTripThroughHttpAndDatabase() throws Exception {
        String[] bodies = {
                """
                {"name":"Restaurante","kind":"SPLIT","toleranceMinutes":5,"workDays":["TUESDAY","SATURDAY"],
                 "slots":[{"start":"19:30","end":"23:30"},{"start":"12:00","end":"16:00"}]}""",
                """
                {"name":"Turnos","kind":"ROTATING_SHIFT","toleranceMinutes":10,"cycleStart":"2026-11-16",
                 "cycle":[{"label":"Manana","start":"07:00","durationMinutes":480},{"rest":true},
                          {"label":"Noche","start":"23:00","durationMinutes":480}]}""",
                """
                {"name":"Guardia","kind":"ON_CALL","toleranceMinutes":15,"start":"08:00","durationMinutes":1440,
                 "mode":"PHYSICAL","dates":["2026-11-20","2026-11-18"]}"""};
        for (String body : bodies) assertEquals(201, post("/api/schedules", body).status(), body);
        createOffice();

        backend.close();
        backend = start(false);
        JsonNode all = get("/api/schedules").body();
        assertEquals(4, all.size());
        JsonNode split = find(all, "Restaurante");
        assertEquals("12:00", split.at("/slots/0/start").asText(), "los tramos se ordenan");
        assertTrue(find(all, "Turnos").at("/cycle/1/rest").asBoolean());
        assertEquals("2026-11-18", find(all, "Guardia").at("/dates/0").asText());
        assertTrue(find(all, "Oficina").get("summary").asText().startsWith("Corrido"));
    }

    private static JsonNode find(JsonNode array, String name) {
        for (JsonNode node : array) if (node.get("name").asText().equals(name)) return node;
        throw new AssertionError("No encontrado: " + name);
    }

    @Test
    void clockEventsAndAuditSurviveARestart() throws Exception {
        post("/api/people", "{\"id\":\"EMP-1\",\"displayName\":\"Persona Uno\"}");
        post("/api/assignments", "{\"scheduleId\":\"" + createOffice() + "\",\"personId\":\"EMP-1\",\"validFrom\":\"2026-11-01\"}");
        clock.set(at(MONDAY, 8, 0));
        post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}");

        backend.close();
        clock.advance(Duration.ofHours(1));
        backend = start(false);

        assertEquals(1, get("/api/clock-events?personId=EMP-1&from=2026-11-16").body().size());
        assertEquals("PRESENT", get("/api/presence/EMP-1").body().get("status").asText());
        assertEquals(5, get("/api/audit").body().size(), "alta de la cuenta, persona, horario, asignacion y fichaje");
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-1\",\"type\":\"ENTRY\",\"source\":\"KIOSK\"}"),
                409, "DUPLICATE_CLOCK_EVENT");
    }

    @Test
    void demoDataLoadsOnlyOnceAndCoversAllScheduleKinds() throws Exception {
        backend.close();
        backend = start(true);
        JsonNode schedules = get("/api/schedules").body();
        assertEquals(5, schedules.size());
        assertEquals(8, get("/api/people").body().size());
        backend.close();
        backend = start(true);
        assertEquals(5, get("/api/schedules").body().size(), "no se duplican al reiniciar");
        assertEquals(8, get("/api/presence").body().size());
    }

    @Test
    void authenticationIsRequiredAndSetupHappensOnce() throws Exception {
        String adminToken = token;
        token = null;
        assertError(get("/api/people"), 401, "UNAUTHENTICATED");
        assertEquals(false, get("/api/auth/status").body().get("setupRequired").asBoolean());
        assertError(post("/api/auth/setup", "{\"username\":\"otro\",\"password\":\"otra-clave-1\"}"),
                409, "SETUP_ALREADY_DONE");
        assertError(post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"mala-clave\"}"),
                401, "INVALID_CREDENTIALS");
        assertError(post("/api/auth/login", "{\"username\":\"nadie\",\"password\":\"mala-clave\"}"),
                401, "INVALID_CREDENTIALS");
        token = "token-inventado";
        assertError(get("/api/auth/me"), 401, "UNAUTHENTICATED");
        token = adminToken;
        assertEquals("ADMIN", get("/api/auth/me").body().get("role").asText());
        assertEquals(204, post("/api/auth/logout", "{}").status());
        assertError(get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void anEmployeeOnlySeesAndClocksTheirOwnData() throws Exception {
        post("/api/people", "{\"id\":\"EMP-1\",\"displayName\":\"Persona Uno\"}");
        post("/api/people", "{\"id\":\"EMP-2\",\"displayName\":\"Persona Dos\"}");
        assertEquals(201, post("/api/workplaces", "{\"id\":\"OFI\",\"name\":\"Oficina\"}").status());
        String office = createOffice();
        for (String person : new String[]{"EMP-1", "EMP-2"})
            assertEquals(201, post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"" + person
                    + "\",\"validFrom\":\"2026-11-01\",\"workplaceId\":\"OFI\"}").status());
        assertError(post("/api/assignments", "{\"scheduleId\":\"" + office + "\",\"personId\":\"EMP-1\","
                + "\"validFrom\":\"2027-01-01\",\"workplaceId\":\"NO\"}"), 404, "WORKPLACE_NOT_FOUND");
        assertEquals(201, post("/api/users", "{\"username\":\"uno\",\"password\":\"clave-uno-1\","
                + "\"role\":\"EMPLOYEE\",\"personId\":\"EMP-1\"}").status());
        assertError(post("/api/users", "{\"username\":\"uno\",\"password\":\"clave-uno-1\","
                + "\"role\":\"EMPLOYEE\",\"personId\":\"EMP-2\"}"), 409, "ACCOUNT_ALREADY_EXISTS");
        assertError(post("/api/users", "{\"username\":\"corta\",\"password\":\"123\","
                + "\"role\":\"EMPLOYEE\",\"personId\":\"EMP-2\"}"), 400, "INVALID_DATA");
        assertError(post("/api/users", "{\"username\":\"sinpersona\",\"password\":\"clave-larga-1\","
                + "\"role\":\"EMPLOYEE\"}"), 400, "INVALID_DATA");

        token = null;
        Reply login = post("/api/auth/login", "{\"username\":\"UNO\",\"password\":\"clave-uno-1\"}");
        assertEquals(200, login.status());
        assertEquals("EMPLOYEE", login.body().at("/user/role").asText());
        assertEquals("Persona Uno", login.body().at("/user/displayName").asText());
        token = login.body().get("token").asText();

        clock.set(at(MONDAY, 8, 2));
        Reply entry = post("/api/clock-events", "{\"type\":\"ENTRY\"}");
        assertEquals(201, entry.status(), String.valueOf(entry.body()));
        assertEquals("EMP-1", entry.body().at("/event/personId").asText());
        assertEquals("WEB", entry.body().at("/event/source").asText());
        assertEquals("OFI", entry.body().at("/event/workplaceId").asText(), "el fichaje guarda el centro");
        assertEquals("Oficina", entry.body().at("/presence/workplace/name").asText());
        assertError(post("/api/clock-events", "{\"personId\":\"EMP-2\",\"type\":\"ENTRY\"}"), 403, "FORBIDDEN");
        assertError(post("/api/clock-events", "{\"type\":\"EXIT\",\"timestamp\":\"2026-11-16T07:00:00Z\"}"),
                403, "FORBIDDEN");
        assertError(post("/api/clock-events", "{\"type\":\"EXIT\",\"source\":\"ADMIN\"}"), 403, "FORBIDDEN");
        assertEquals(1, get("/api/clock-events?from=2026-11-16").body().size());
        assertError(get("/api/clock-events?personId=EMP-2&from=2026-11-16"), 403, "FORBIDDEN");
        assertEquals("PRESENT", get("/api/presence/EMP-1").body().get("status").asText());
        assertError(get("/api/presence/EMP-2"), 403, "FORBIDDEN");
        assertEquals(200, get("/api/reports/daily?from=2026-11-16").status());
        assertEquals(1, get("/api/assignments").body().size(), "solo su asignacion");
        assertEquals(200, get("/api/schedules").status());
        for (String adminOnly : new String[]{"/api/presence", "/api/people", "/api/users", "/api/audit"})
            assertError(get(adminOnly), 403, "FORBIDDEN");
        assertError(post("/api/holidays", "{\"date\":\"2026-12-24\",\"description\":\"x\"}"), 403, "FORBIDDEN");

        // El responsable cambia la clave: se cierran las sesiones del empleado.
        String employeeToken = token;
        token = null;
        token = post("/api/auth/login", "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}")
                .body().get("token").asText();
        assertEquals(204, post("/api/users/uno/password", "{\"password\":\"nueva-clave-1\"}").status());
        token = employeeToken;
        assertError(get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void corsPreflightIsAnswered() throws Exception {
        HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri("/api/clock-events"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:8081").build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(204, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().contains("Authorization"));
    }
}
