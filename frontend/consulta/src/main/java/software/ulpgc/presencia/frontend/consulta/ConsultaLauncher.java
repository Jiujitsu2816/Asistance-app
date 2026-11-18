package software.ulpgc.presencia.frontend.consulta;

import javafx.application.Application;

/** Punto de entrada (no extiende Application para poder ejecutarse sin module-path). */
public final class ConsultaLauncher {
    private ConsultaLauncher() { }

    public static void main(String[] args) {
        Application.launch(ConsultaApp.class, args);
    }
}
