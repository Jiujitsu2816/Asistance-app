package software.ulpgc.presencia.frontend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import software.ulpgc.presencia.frontend.api.ApiModel.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Implementacion HTTP + JSON de {@link PresenceApi} con el cliente HTTP del JDK. */
public final class HttpPresenceApi implements PresenceApi {
    private final URI baseUrl;
    private final String actor;
    private final HttpClient http;
    private final ObjectMapper json;

    /**
     * @param actor nombre enviado en la cabecera X-Actor para la auditoria del backend.
     */
    public HttpPresenceApi(URI baseUrl, String actor) {
        this.baseUrl = baseUrl;
        this.actor = actor;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JavaTimeModule time = new JavaTimeModule();
        time.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
        this.json = new ObjectMapper().registerModule(time)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    @Override public List<Person> listPeople() { return get("/people", Map.of(), new TypeReference<>() { }); }

    @Override public Person createPerson(Person person) { return post("/people", person, Person.class); }

    @Override public List<Schedule> listSchedules() { return get("/schedules", Map.of(), new TypeReference<>() { }); }

    @Override public Schedule createSchedule(Schedule schedule) { return post("/schedules", schedule, Schedule.class); }

    @Override public List<Assignment> listAssignments() {
        return get("/assignments", Map.of(), new TypeReference<>() { });
    }

    @Override public Assignment assignSchedule(AssignmentRequest request) {
        return post("/assignments", request, Assignment.class);
    }

    @Override public List<Holiday> listHolidays() { return get("/holidays", Map.of(), new TypeReference<>() { }); }

    @Override public Holiday createHoliday(Holiday holiday) { return post("/holidays", holiday, Holiday.class); }

    @Override public ClockResult clock(ClockRequest request) { return post("/clock-events", request, ClockResult.class); }

    @Override public List<ClockEvent> listClockEvents(String personId, LocalDate from, LocalDate to) {
        return get("/clock-events", params("personId", personId, "from", from, "to", to), new TypeReference<>() { });
    }

    @Override public List<Presence> currentPresence() { return get("/presence", Map.of(), new TypeReference<>() { }); }

    @Override public Presence presenceOf(String personId) {
        return get("/presence/" + encode(personId), Map.of(), new TypeReference<>() { });
    }

    @Override public Report dailyReport(String personId, LocalDate from, LocalDate to) {
        return get("/reports/daily", params("personId", personId, "from", from, "to", to), new TypeReference<>() { });
    }

    @Override public List<Incident> listIncidents(String personId, LocalDate from, LocalDate to, String type) {
        return get("/incidents", params("personId", personId, "from", from, "to", to, "type", type),
                new TypeReference<>() { });
    }

    @Override public Justification justifyIncident(String incidentKey, String reason) {
        return post("/incidents/justifications", Map.of("incidentKey", incidentKey, "reason", reason == null ? "" : reason),
                Justification.class);
    }

    @Override public List<AuditEntry> audit(int limit) {
        return get("/audit", params("limit", limit), new TypeReference<>() { });
    }

    // ------------------------------------------------------------------------------------

    private <T> T get(String path, Map<String, String> query, TypeReference<T> type) {
        String suffix = query.isEmpty() ? "" : "?" + query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue())).collect(Collectors.joining("&"));
        HttpRequest request = request(path + suffix).GET().build();
        return send(request, body -> json.readValue(body, type));
    }

    private <T> T post(String path, Object body, Class<T> type) {
        String payload;
        try {
            payload = json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new ApiException(ApiException.INVALID_RESPONSE, "No se pudo preparar la peticion", 0, e);
        }
        HttpRequest request = request(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        return send(request, response -> json.readValue(response, type));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("X-Actor", actor);
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(String body) throws IOException;
    }

    private <T> T send(HttpRequest request, Reader<T> reader) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(ApiException.NETWORK_ERROR,
                    "No se pudo conectar con el backend (" + baseUrl + "). ¿Está arrancado?", 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ApiException.NETWORK_ERROR, "Peticion interrumpida", 0, e);
        }
        try {
            if (response.statusCode() >= 400) {
                JsonNode error = json.readTree(response.body());
                throw new ApiException(error.path("error").asText("HTTP_" + response.statusCode()),
                        error.path("message").asText("Error " + response.statusCode()), response.statusCode(), null);
            }
            return reader.read(response.body());
        } catch (IOException e) {
            throw new ApiException(ApiException.INVALID_RESPONSE,
                    "Respuesta no valida del backend (HTTP " + response.statusCode() + ")", response.statusCode(), e);
        }
    }

    private static Map<String, String> params(Object... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
            if (pairs[i + 1] != null && !pairs[i + 1].toString().isBlank())
                result.put(pairs[i].toString(), pairs[i + 1].toString());
        return result;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
