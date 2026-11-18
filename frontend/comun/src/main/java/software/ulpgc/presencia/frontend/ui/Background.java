package software.ulpgc.presencia.frontend.ui;

import javafx.application.Platform;
import software.ulpgc.presencia.frontend.api.ApiException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Ejecuta las llamadas a la API fuera del hilo de JavaFX para que la interfaz no se bloquee
 * y entrega el resultado (o el error) de nuevo en el hilo de JavaFX.
 */
public final class Background {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "presencia-api");
        thread.setDaemon(true);
        return thread;
    });

    private Background() { }

    /**
     * Carga inicial de una aplicacion: si el backend aun no esta escuchando (por ejemplo,
     * al arrancar todo a la vez desde IntelliJ) reintenta durante unos 20 segundos.
     */
    public static <T> void runAtStartup(Callable<T> call, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        run(() -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    return call.call();
                } catch (ApiException e) {
                    if (!ApiException.NETWORK_ERROR.equals(e.code()) || attempt >= 20) throw e;
                    Thread.sleep(1000);
                }
            }
        }, onSuccess, onError);
    }

    public static <T> void run(Callable<T> call, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = call.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable error) {
                Platform.runLater(() -> onError.accept(error));
            }
        });
    }
}
