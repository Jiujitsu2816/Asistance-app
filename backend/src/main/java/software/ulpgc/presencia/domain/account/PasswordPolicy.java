package software.ulpgc.presencia.domain.account;

import software.ulpgc.presencia.domain.Require;

/** Requisitos minimos de las claves. */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() { }

    public static char[] check(char[] password) {
        Require.that(password != null && password.length >= MIN_LENGTH && password.length <= MAX_LENGTH,
                "La clave debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres");
        boolean blank = true;
        for (char c : password) if (!Character.isWhitespace(c)) blank = false;
        Require.that(!blank, "La clave no puede estar en blanco");
        return password;
    }
}
