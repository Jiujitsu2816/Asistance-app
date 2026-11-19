package software.ulpgc.presencia.domain.account;

import software.ulpgc.presencia.domain.Require;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Cuenta de acceso. Nunca contiene la clave en claro: passwordHash es el resultado del
 * adaptador PasswordHasher (sal aleatoria incluida).
 *
 * @param personId persona a la que representa; obligatoria para EMPLOYEE y opcional para ADMIN.
 */
public record UserAccount(String username, Role role, String personId, String passwordHash, Instant createdAt) {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,31}");

    public UserAccount {
        username = normalize(username);
        Require.that(USERNAME.matcher(username).matches(),
                "El usuario debe tener entre 3 y 32 caracteres: minusculas, numeros, '.', '-' o '_'");
        Require.present(role, "role");
        personId = Require.optionalText(personId, "personId", 32);
        Require.that(role != Role.EMPLOYEE || personId != null,
                "Una cuenta de empleado debe estar asociada a una persona");
        passwordHash = Require.text(passwordHash, "passwordHash", 300);
        Require.present(createdAt, "createdAt");
    }

    public static String normalize(String username) {
        return Require.text(username, "username", 32).toLowerCase();
    }

    public UserAccount withPasswordHash(String newHash) {
        return new UserAccount(username, role, personId, newHash, createdAt);
    }
}
