package software.ulpgc.presencia.frontend.ui;

import javafx.scene.control.Label;

/** Etiqueta de estado con color segun su gravedad (ok, info, warn, bad, muted). */
public final class Badge {
    private Badge() { }

    public static Label of(String code) {
        Label label = new Label(Texts.status(code));
        label.getStyleClass().addAll("badge", Texts.TONES.getOrDefault(code, "muted"));
        label.setUserData(code);
        return label;
    }
}
