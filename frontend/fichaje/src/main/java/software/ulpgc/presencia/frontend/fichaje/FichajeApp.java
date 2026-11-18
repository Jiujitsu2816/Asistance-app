package software.ulpgc.presencia.frontend.fichaje;

import javafx.application.Application;
import javafx.stage.Stage;
import software.ulpgc.presencia.frontend.api.ApiConfig;
import software.ulpgc.presencia.frontend.api.HttpPresenceApi;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.net.URI;
import java.time.Clock;

/** Aplicacion JavaFX de registro de asistencia. */
public final class FichajeApp extends Application {
    @Override
    public void start(Stage stage) {
        URI url = ApiConfig.baseUrl();
        FichajeView view = new FichajeView(new HttpPresenceApi(url, "app-fichaje"), Clock.systemDefaultZone());
        stage.setTitle("Registro de asistencia");
        stage.setScene(Ui.scene("Registro de asistencia", "Fichar entrada y salida", url.toString(), view, 980, 720));
        stage.show();
    }
}
