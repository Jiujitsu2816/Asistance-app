package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;

import java.util.List;

/** Cuentas de acceso (solo responsables). */
public interface AccountAdministrationUseCase {
    record CreateAccountCommand(String username, char[] password, Role role, String personId, String actor) { }

    UserAccount create(CreateAccountCommand command);

    List<UserAccount> list();

    /** Cambia la clave y cierra las sesiones abiertas de esa cuenta. */
    void resetPassword(String username, char[] password, String actor);
}
