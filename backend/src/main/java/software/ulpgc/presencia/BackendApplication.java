package software.ulpgc.presencia;

import software.ulpgc.presencia.infrastructure.config.AppConfig;

import java.time.Clock;

/** Punto de entrada del backend: java -jar backend/target/presencia-backend.jar */
public final class BackendApplication {
    private BackendApplication() { }

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        Backend backend = new Backend(config, Clock.system(config.zone())).start();
        Runtime.getRuntime().addShutdownHook(new Thread(backend::close, "backend-shutdown"));
        System.out.printf("Backend de control de presencia escuchando en http://%s:%d/api (zona %s, BD %s)%n",
                config.host(), backend.port(), config.zone(), config.dbUrl());
    }
}
