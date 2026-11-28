package software.ulpgc.presencia.domain;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;

/** Validaciones comunes de los objetos del dominio. Lanzan siempre INVALID_DATA. */
public final class Require {
    private Require() { }

    public static <T> T present(T value, String field) {
        if (value == null) throw new DomainException(ErrorCode.INVALID_DATA, "Falta el campo obligatorio '" + field + "'");
        return value;
    }

    public static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank())
            throw new DomainException(ErrorCode.INVALID_DATA, "Falta el campo obligatorio '" + field + "'");
        String trimmed = value.strip();
        if (trimmed.length() > maxLength)
            throw new DomainException(ErrorCode.INVALID_DATA, "El campo '" + field + "' admite como maximo " + maxLength + " caracteres");
        return trimmed;
    }

    public static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) return null;
        return text(value, field, maxLength);
    }

    /** Las horas de un horario se expresan en minutos exactos (HH:mm). */
    public static java.time.LocalTime minutePrecision(java.time.LocalTime time, String field) {
        present(time, field);
        that(time.getSecond() == 0 && time.getNano() == 0, "La hora '" + field + "' debe tener formato HH:mm");
        return time;
    }

    public static void that(boolean condition, String message) {
        if (!condition) throw new DomainException(ErrorCode.INVALID_DATA, message);
    }
}
