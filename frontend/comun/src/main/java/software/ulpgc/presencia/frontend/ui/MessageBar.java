package software.ulpgc.presencia.frontend.ui;

import javafx.scene.control.Label;
import software.ulpgc.presencia.frontend.api.ApiException;

/** Barra de mensajes: confirmaciones y errores devueltos por la API. */
public final class MessageBar extends Label {
    private String code;

    public MessageBar() {
        setWrapText(true);
        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().add("message");
        clear();
    }

    public void ok(String text) {
        show(text, "ok", "OK");
    }

    public void error(String prefix, Throwable error) {
        String errorCode = error instanceof ApiException api ? api.code() : "ERROR";
        show(prefix + " " + describe(error), "error", errorCode);
    }

    public void clear() {
        code = null;
        setText("");
        setManaged(false);
        setVisible(false);
    }

    /** "OK" tras un mensaje de exito, el codigo del contrato tras un error, o null. */
    public String code() {
        return code;
    }

    public static String describe(Throwable error) {
        if (error instanceof ApiException api) return api.getMessage() + " (" + api.code() + ")";
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private void show(String text, String style, String code) {
        this.code = code;
        getStyleClass().removeAll("ok", "error");
        getStyleClass().add(style);
        setText(text);
        setManaged(true);
        setVisible(true);
    }
}
