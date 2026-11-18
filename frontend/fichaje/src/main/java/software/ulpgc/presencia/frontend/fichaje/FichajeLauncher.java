package software.ulpgc.presencia.frontend.fichaje;

import javafx.application.Application;

/**
 * Punto de entrada. Una clase main que no extiende Application permite ejecutar la app
 * desde IntelliJ o con java -jar sin configurar el module-path de JavaFX.
 */
public final class FichajeLauncher {
    private FichajeLauncher() { }

    public static void main(String[] args) {
        Application.launch(FichajeApp.class, args);
    }
}
