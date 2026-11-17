package software.ulpgc.presencia.domain;

import software.ulpgc.presencia.domain.error.InvalidDataException;

/** Validaciones comunes de los objetos del dominio. Lanzan siempre INVALID_DATA. */
public final class Require {
    private Require() { }

    public static <T> T present(T value, String field) {
        if (value == null) throw new InvalidDataException("Falta el campo obligatorio '" + field + "'");
        return value;
    }

    public static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank())
            throw new InvalidDataException("Falta el campo obligatorio '" + field + "'");
        String trimmed = value.strip();
        if (trimmed.length() > maxLength)
            throw new InvalidDataException("El campo '" + field + "' admite como maximo " + maxLength + " caracteres");
        return trimmed;
    }

    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return text(value, field, maxLength);
    }

    public static void that(boolean condition, String message) {
        if (!condition) throw new InvalidDataException(message);
    }
}
