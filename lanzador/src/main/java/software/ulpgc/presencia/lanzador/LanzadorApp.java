package software.ulpgc.presencia.lanzador;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import software.ulpgc.presencia.frontend.api.ApiConfig;
import software.ulpgc.presencia.frontend.consulta.ConsultaApp;
import software.ulpgc.presencia.frontend.fichaje.FichajeApp;
import software.ulpgc.presencia.frontend.horarios.HorariosApp;
import software.ulpgc.presencia.frontend.ui.Ui;

import java.util.function.Supplier;

/** Ventana del lanzador: abre cada aplicacion en su propia ventana. Al cerrarla, se cierra todo. */
public final class LanzadorApp extends Application {
    @Override
    public void start(Stage stage) {
        Label status = new Label(Lanzador.status);
        status.setWrapText(true);
        status.getStyleClass().add(Lanzador.status.startsWith("No se pudo") ? "negative" : "sub");

        Button fichaje = button("Registro de asistencia", FichajeApp::new);
        Button consulta = button("Consulta de presencia", ConsultaApp::new);
        Button horarios = button("Definición de horarios", HorariosApp::new);
        Button all = new Button("Abrir las tres");
        all.getStyleClass().add("secondary");
        all.setMaxWidth(Double.MAX_VALUE);
        all.setOnAction(e -> {
            fichaje.fire();
            consulta.fire();
            horarios.fire();
        });

        VBox content = Ui.page(Ui.card("Aplicaciones", fichaje, consulta, horarios, all),
                Ui.card("Estado", status, Ui.hint("Al cerrar esta ventana se cierran las aplicaciones y el backend.")));
        stage.setTitle("Control de presencia");
        stage.setScene(Ui.scene("Control de presencia", "Lanzador", ApiConfig.baseUrl().toString(), content, 560, 470));
        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();
    }

    private static Button button(String text, Supplier<Application> app) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> {
            try {
                app.get().start(new Stage());
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo abrir " + text, ex);
            }
        });
        return button;
    }
}
