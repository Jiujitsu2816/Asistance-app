package software.ulpgc.presencia.infrastructure.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Enrutador minimo: metodo + plantilla de ruta con segmentos {variable}. */
public final class Router {
    @FunctionalInterface
    public interface Handler {
        ApiResponse handle(ApiRequest request) throws Exception;
    }

    record Route(String method, String[] segments, Handler handler) { }

    record Match(Handler handler, Map<String, String> pathParams) { }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, Handler handler) {
        return add("GET", path, handler);
    }

    public Router post(String path, Handler handler) {
        return add("POST", path, handler);
    }

    private Router add(String method, String path, Handler handler) {
        routes.add(new Route(method, split(path), handler));
        return this;
    }

    /** Ruta que coincide; vacio si no existe. pathExists indica si existe con otro metodo. */
    Optional<Match> find(String method, String path) {
        String[] segments = split(path);
        for (Route route : routes) {
            if (!route.method().equals(method)) continue;
            Map<String, String> params = match(route.segments(), segments);
            if (params != null) return Optional.of(new Match(route.handler(), params));
        }
        return Optional.empty();
    }

    boolean pathExists(String path) {
        String[] segments = split(path);
        return routes.stream().anyMatch(r -> match(r.segments(), segments) != null);
    }

    private static Map<String, String> match(String[] template, String[] actual) {
        if (template.length != actual.length) return null;
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < template.length; i++) {
            String t = template[i];
            if (t.startsWith("{") && t.endsWith("}")) params.put(t.substring(1, t.length() - 1), actual[i]);
            else if (!t.equals(actual[i])) return null;
        }
        return params;
    }

    private static String[] split(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/+");
    }
}
