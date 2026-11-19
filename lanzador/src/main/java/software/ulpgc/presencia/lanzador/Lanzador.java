package software.ulpgc.presencia.lanzador;

import software.ulpgc.presencia.Backend;
import software.ulpgc.presencia.frontend.FrontendServer;
import software.ulpgc.presencia.infrastructure.config.AppConfig;

import java.awt.Desktop;
import java.net.URI;
import java.time.Clock;

/**
 * Arranque "todo en uno": inicia el backend (API HTTP/JSON, el broker que responde a la
 * aplicacion) y el servidor de la aplicacion web en este mismo proceso, y abre el navegador.
 * La aplicacion web sigue hablando con el backend por HTTP, igual que si se ejecutaran por
 * separado. Se detiene con Ctrl+C o cerrando la ventana de la consola.
 */
public final class Lanzador {
    private Lanzador() { }

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        Backend backend = new Backend(config, Clock.system(config.zone())).start();
        String api = "http://" + config.host() + ":" + backend.port() + "/api";
        String host = setting("presencia.frontend.host", "localhost");
        int port = Integer.parseInt(setting("presencia.frontend.port", "8081"));
        FrontendServer web;
        try {
            web = new FrontendServer(host, port, api);
        } catch (Exception e) {
            backend.close();
            throw new IllegalStateException("No se pudo abrir el puerto " + port + " de la aplicacion web: " + e.getMessage(), e);
        }
        web.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            web.close();
            backend.close();
        }, "apagado"));

        String url = "http://" + host + ":" + web.port() + "/";
        System.out.println();
        System.out.println("  Control de presencia en marcha");
        System.out.println("  Aplicacion: " + url);
        System.out.println("  API:        " + api);
        System.out.println("  Pulsa Ctrl+C (o cierra esta ventana) para detenerlo.");
        System.out.println();
        openBrowser(url);
    }

    private static String setting(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null) value = System.getenv(key.toUpperCase().replace('.', '_').replace('-', '_'));
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static void openBrowser(String url) {
        if (Boolean.getBoolean("presencia.no-browser")) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(URI.create(url));
            else System.out.println("  Abre la direccion anterior en el navegador.");
        } catch (Exception e) {
            System.out.println("  No se pudo abrir el navegador automaticamente; abre " + url);
        }
    }
}
