package software.ulpgc.presencia.infrastructure.persistence;

import software.ulpgc.presencia.application.port.out.Transactions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Acceso JDBC a la base de datos. Si hay una transaccion abierta en el hilo actual, los
 * repositorios reutilizan su conexion; si no, cada operacion usa una conexion propia.
 */
public final class Database implements Transactions {
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final String url;
    private final String user;
    private final String password;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Crea las tablas si no existen (schema.sql es idempotente). */
    public void migrate() {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IllegalStateException("No se encuentra db/schema.sql");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("No se pudo leer el esquema", e);
        }
        inTransaction(() -> run(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    String trimmed = sql.lines().filter(l -> !l.strip().startsWith("--"))
                            .reduce("", (a, b) -> a + "\n" + b).strip();
                    if (!trimmed.isEmpty()) statement.execute(trimmed);
                }
            }
            return null;
        }));
    }

    public <T> T run(SqlWork<T> work) {
        Connection open = current.get();
        try {
            if (open != null) return work.run(open);
            try (Connection connection = connect()) {
                return work.run(connection);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error de base de datos: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (current.get() != null) return work.get();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            current.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            } finally {
                current.remove();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error de transaccion: " + e.getMessage(), e);
        }
    }

    /** Cierra la base de datos embebida y libera el fichero. */
    public void shutdown() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo cerrar la base de datos", e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
