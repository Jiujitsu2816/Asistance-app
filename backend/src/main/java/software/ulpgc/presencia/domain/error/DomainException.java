package software.ulpgc.presencia.domain.error;

/**
 * Error de negocio con un codigo estable. El adaptador HTTP traduce cada codigo a un
 * estado HTTP; el dominio no conoce HTTP.
 */
public abstract class DomainException extends RuntimeException {
    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
