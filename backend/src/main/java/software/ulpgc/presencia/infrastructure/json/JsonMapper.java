package software.ulpgc.presencia.infrastructure.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Configuracion unica de Jackson: fechas ISO-8601 y rechazo de campos desconocidos. */
public final class JsonMapper {
    private JsonMapper() { }

    public static ObjectMapper create() {
        JavaTimeModule time = new JavaTimeModule();
        // Las horas de los horarios se intercambian como "HH:mm".
        time.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm")));
        return new ObjectMapper()
                .registerModule(time)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
