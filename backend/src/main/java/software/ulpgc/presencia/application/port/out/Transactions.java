package software.ulpgc.presencia.application.port.out;

import java.util.function.Supplier;

/** Ejecuta una operacion de forma atomica (el cambio y su registro de auditoria). */
public interface Transactions {
    <T> T inTransaction(Supplier<T> work);
}
