package software.ulpgc.presencia.domain.error;

/**
 * Error de negocio con un codigo estable ({@link ErrorCode}). Una sola clase basta: lo que
 * distingue a cada error es su codigo, que el adaptador HTTP traduce a un estado HTTP. El
 * dominio no conoce HTTP.
 */
public final class DomainException extends RuntimeException {
    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
