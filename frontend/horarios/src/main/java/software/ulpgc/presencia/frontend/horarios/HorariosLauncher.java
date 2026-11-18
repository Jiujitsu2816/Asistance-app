package software.ulpgc.presencia.frontend.horarios;

import javafx.application.Application;

/** Punto de entrada (no extiende Application para poder ejecutarse sin module-path). */
public final class HorariosLauncher {
    private HorariosLauncher() { }

    public static void main(String[] args) {
        Application.launch(HorariosApp.class, args);
    }
}
