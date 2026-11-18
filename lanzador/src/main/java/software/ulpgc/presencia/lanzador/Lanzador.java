package software.ulpgc.presencia.lanzador;

import javafx.application.Application;
import software.ulpgc.presencia.Backend;
import software.ulpgc.presencia.infrastructure.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

/**
 * Arranque "todo en uno": inicia el backend en este mismo proceso y abre una ventana desde la
 * que lanzar las tres aplicaciones. Las aplicaciones siguen comunicandose con el backend por
 * HTTP, igual que si se ejecutaran por separado.
 *
 * <p>Si el puerto ya esta ocupado por un backend en marcha, se reutiliza ese backend.
 */
public final class Lanzador {
    private static Backend backend;
    static String status;

    private Lanzador() { }

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        String url = "http://" + config.host() + ":" + config.port() + "/api";
        try {
            backend = new Backend(config, Clock.system(config.zone())).start();
            url = "http://" + config.host() + ":" + backend.port() + "/api";
            status = "Backend iniciado en " + url;
        } catch (Exception e) {
            if (isRunning(url)) {
                status = "Se usa el backend que ya estaba en marcha en " + url;
            } else {
                status = "No se pudo iniciar el backend: " + e.getMessage();
                System.err.println(status);
            }
        }
        System.out.println(status);
        // Las tres aplicaciones leen la URL de la API de esta propiedad (ApiConfig).
        System.setProperty("presencia.api.url", url);
        Runtime.getRuntime().addShutdownHook(new Thread(Lanzador::stopBackend, "backend-shutdown"));
        Application.launch(LanzadorApp.class, args);
        stopBackend();
        System.exit(0);
    }

    private static synchronized void stopBackend() {
        if (backend != null) {
            backend.close();
            backend = null;
        }
    }

    private static boolean isRunning(String url) {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url + "/health")).timeout(Duration.ofSeconds(3)).build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
