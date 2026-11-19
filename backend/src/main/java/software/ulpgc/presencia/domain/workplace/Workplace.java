package software.ulpgc.presencia.domain.workplace;

import software.ulpgc.presencia.domain.Require;

import java.util.regex.Pattern;

/**
 * Centro de trabajo (edificio, tienda, planta de hospital...). Cada asignacion de horario
 * indica en que centro se trabaja y cada fichaje guarda el centro para la trazabilidad.
 */
public record Workplace(String id, String name, String address) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,31}");

    public Workplace {
        id = Require.text(id, "id", 32);
        Require.that(ID.matcher(id).matches(),
                "El codigo del centro solo admite letras, numeros, '-' y '_' (maximo 32 caracteres)");
        name = Require.text(name, "name", 80);
        address = Require.optionalText(address, "address", 120);
    }
}
