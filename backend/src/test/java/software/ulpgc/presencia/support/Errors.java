package software.ulpgc.presencia.support;

import org.junit.jupiter.api.function.Executable;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Comprueba que una operacion falla con un codigo de error concreto. */
public final class Errors {
    private Errors() { }

    public static DomainException assertError(ErrorCode code, Executable operation) {
        return assertError(code, operation, null);
    }

    public static DomainException assertError(ErrorCode code, Executable operation, String message) {
        DomainException error = assertThrows(DomainException.class, operation, message);
        assertEquals(code, error.code(), message);
        return error;
    }
}
