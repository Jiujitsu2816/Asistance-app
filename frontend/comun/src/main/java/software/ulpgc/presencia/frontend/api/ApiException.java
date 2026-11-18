package software.ulpgc.presencia.frontend.api;

/** Error devuelto por la API (codigo estable del contrato) o de comunicacion. */
public final class ApiException extends RuntimeException {
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";

    private final String code;
    private final int status;

    public ApiException(String code, String message, int status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
