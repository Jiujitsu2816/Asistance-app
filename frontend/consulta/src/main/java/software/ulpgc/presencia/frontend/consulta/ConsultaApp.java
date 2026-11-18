package software.ulpgc.presencia.frontend.consulta;

import javafx.application.Application;
import javafx.stage.Stage;
import software.ulpgc.presencia.frontend.api.ApiConfig;
import software.ulpgc.presencia.frontend.api.HttpPresenceApi;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.net.URI;
import java.time.Clock;

/** Aplicacion JavaFX de consulta de presencia e incidencias. */
public final class ConsultaApp extends Application {
    @Override
    public void start(Stage stage) {
        URI url = ApiConfig.baseUrl();
        ConsultaView view = new ConsultaView(new HttpPresenceApi(url, "app-consulta"), Clock.systemDefaultZone());
        stage.setTitle("Consulta de presencia");
        stage.setScene(Ui.scene("Consulta de presencia", "Presencia, informes e incidencias", url.toString(), view,
                1280, 820));
        stage.show();
    }
}
