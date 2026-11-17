package software.ulpgc.presencia.domain.person;

import software.ulpgc.presencia.domain.Require;

import java.util.regex.Pattern;

/**
 * Persona a la que se controla la presencia. El identificador es un codigo interno
 * (p. ej. EMP-001), nunca un documento de identidad real.
 *
 * @param team equipo o grupo opcional; permite asignar un horario a todo un grupo.
 */
public record Person(String id, String displayName, String team) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,31}");

    public Person {
        id = Require.text(id, "id", 32);
        Require.that(ID.matcher(id).matches(),
                "El id solo admite letras, numeros, '-' y '_' (maximo 32 caracteres)");
        displayName = Require.text(displayName, "displayName", 80);
        team = Require.optionalText(team, "team", 40);
    }

    public boolean belongsTo(String teamName) {
        return team != null && team.equalsIgnoreCase(teamName);
    }
}
