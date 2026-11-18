package software.ulpgc.presencia.frontend.ui;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Textos en espanol de los codigos del contrato y formatos de fecha, hora y duracion. */
public final class Texts {
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final Map<String, String> STATUS = Map.ofEntries(
            // Estado diario
            Map.entry("NO_SCHEDULE", "Sin horario"), Map.entry("HOLIDAY", "Festivo"), Map.entry("REST", "Descanso"),
            Map.entry("PENDING", "Pendiente"), Map.entry("IN_PROGRESS", "En curso"),
            Map.entry("COMPLETED", "Cumplido"), Map.entry("EXTRA_WORK", "Trabajo extra"),
            Map.entry("JUSTIFIED", "Justificado"), Map.entry("EARLY_DEPARTURE", "Salida anticipada"),
            Map.entry("LATE", "Retraso"), Map.entry("CHECKOUT_PENDING", "Salida pendiente"),
            Map.entry("ABSENT", "Ausente"),
            // Presencia actual
            Map.entry("PRESENT", "Presente"), Map.entry("PRESENT_OUT_OF_SCHEDULE", "Presente (fuera de horario)"),
            Map.entry("EXPECTED", "Esperado"), Map.entry("ON_CALL", "Guardia localizada"),
            Map.entry("OFF_DUTY", "Fuera de turno"),
            // Incidencias
            Map.entry("ABSENCE", "Ausencia"), Map.entry("LATE_ARRIVAL", "Retraso"),
            Map.entry("MISSING_CHECKOUT", "Salida pendiente"), Map.entry("UNSCHEDULED_WORK", "Fuera de horario"));

    public static final Map<String, String> KINDS = Map.of(
            "CONTINUOUS", "Corrido", "SPLIT", "Partido", "ROTATING_SHIFT", "Turnos rotatorios", "ON_CALL", "Guardia");

    public static final Map<String, String> TONES = Map.ofEntries(
            Map.entry("PRESENT", "ok"), Map.entry("COMPLETED", "ok"), Map.entry("JUSTIFIED", "ok"),
            Map.entry("IN_PROGRESS", "info"), Map.entry("PRESENT_OUT_OF_SCHEDULE", "info"),
            Map.entry("EXPECTED", "info"), Map.entry("ON_CALL", "info"), Map.entry("EXTRA_WORK", "info"),
            Map.entry("UNSCHEDULED_WORK", "info"),
            Map.entry("LATE", "warn"), Map.entry("LATE_ARRIVAL", "warn"), Map.entry("EARLY_DEPARTURE", "warn"),
            Map.entry("CHECKOUT_PENDING", "warn"), Map.entry("MISSING_CHECKOUT", "warn"),
            Map.entry("ABSENT", "bad"), Map.entry("ABSENCE", "bad"));

    private Texts() { }

    public static String status(String code) {
        return code == null ? "—" : STATUS.getOrDefault(code, code);
    }

    public static String kind(String code) {
        return KINDS.getOrDefault(code, code);
    }

    public static String hour(OffsetDateTime value) {
        return value == null ? "—" : HOUR.format(value);
    }

    public static String date(LocalDate value) {
        return value == null ? "—" : DATE.format(value);
    }

    public static String dateTime(OffsetDateTime value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }

    /** 425 -> "7 h 05 min"; -30 -> "−0 h 30 min"; null -> "—". */
    public static String minutes(Long value) {
        if (value == null) return "—";
        long abs = Math.abs(value);
        return (value < 0 ? "−" : "") + abs / 60 + " h " + String.format("%02d", abs % 60) + " min";
    }

    /** "Mañana 07:00–15:00"; si la etiqueta ya son las horas ("08:00-15:00") no se repite. */
    public static String period(String label, OffsetDateTime start, OffsetDateTime end) {
        String hours = hour(start) + "–" + hour(end);
        return label == null || label.matches("\\d{2}:\\d{2}-\\d{2}:\\d{2}") ? hours : label + " " + hours;
    }

    public static String clockType(String type) {
        return "ENTRY".equals(type) ? "Entrada" : "EXIT".equals(type) ? "Salida" : type;
    }
}
