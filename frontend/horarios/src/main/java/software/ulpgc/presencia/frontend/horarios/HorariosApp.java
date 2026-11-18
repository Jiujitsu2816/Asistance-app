package software.ulpgc.presencia.frontend.horarios;

import javafx.application.Application;
import javafx.stage.Stage;
import software.ulpgc.presencia.frontend.api.ApiConfig;
import software.ulpgc.presencia.frontend.api.HttpPresenceApi;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.net.URI;
import java.time.Clock;

/** Aplicacion JavaFX de definicion de horarios. */
public final class HorariosApp extends Application {
    @Override
    public void start(Stage stage) {
        URI url = ApiConfig.baseUrl();
        HorariosView view = new HorariosView(new HttpPresenceApi(url, "app-horarios"), Clock.systemDefaultZone());
        stage.setTitle("Definición de horarios");
        stage.setScene(Ui.scene("Definición de horarios", "Horarios, turnos, guardias y calendario", url.toString(),
                view, 1200, 860));
        stage.show();
    }
}
