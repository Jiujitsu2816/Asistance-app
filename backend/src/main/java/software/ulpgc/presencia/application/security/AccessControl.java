package software.ulpgc.presencia.application.security;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;

/**
 * Reglas de permisos:
 * <ul>
 *   <li>ADMIN puede consultar y modificar todo.</li>
 *   <li>EMPLOYEE solo puede consultar y fichar sus propios datos.</li>
 * </ul>
 */
public final class AccessControl {
    private AccessControl() { }

    public static void requireAdmin(Principal principal) {
        if (!principal.isAdmin()) throw new DomainException(ErrorCode.FORBIDDEN, "Esta operacion solo la puede realizar un responsable");
    }

    /**
     * Persona sobre la que se permite operar. Un empleado solo puede indicar su propia persona
     * (o ninguna, y se usa la suya); un responsable puede indicar cualquiera o ninguna (null).
     */
    public static String personScope(Principal principal, String requestedPersonId) {
        String requested = requestedPersonId == null || requestedPersonId.isBlank() ? null : requestedPersonId.strip();
        if (principal.isAdmin()) return requested;
        if (requested != null && !requested.equals(principal.personId()))
            throw new DomainException(ErrorCode.FORBIDDEN, "Solo puedes consultar o registrar tus propios datos");
        return principal.personId();
    }
}
