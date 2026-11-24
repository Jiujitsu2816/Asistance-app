package software.ulpgc.presencia;

import software.ulpgc.presencia.application.service.AccountAdministrationService;
import software.ulpgc.presencia.domain.account.UserAccount;
import software.ulpgc.presencia.infrastructure.config.AppConfig;
import software.ulpgc.presencia.infrastructure.persistence.Database;
import software.ulpgc.presencia.infrastructure.persistence.JdbcAuditTrail;
import software.ulpgc.presencia.infrastructure.persistence.JdbcPersonRepository;
import software.ulpgc.presencia.infrastructure.persistence.JdbcUserAccountRepository;
import software.ulpgc.presencia.infrastructure.persistence.PersistenceException;
import software.ulpgc.presencia.infrastructure.security.InMemorySessionRepository;
import software.ulpgc.presencia.infrastructure.security.Pbkdf2PasswordHasher;

import java.io.PrintStream;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

/**
 * Recuperacion local de acceso, para cuando se olvida la clave del unico responsable.
 * Se ejecuta en el equipo del servidor, con el backend PARADO (la base de datos queda
 * bloqueada mientras el backend esta en marcha):
 * <pre>
 *   java -jar backend/target/presencia-backend-app.jar --recuperar-acceso            (lista las cuentas)
 *   java -jar backend/target/presencia-backend-app.jar --recuperar-acceso USUARIO    (clave nueva)
 * </pre>
 * La clave nueva es aleatoria, se muestra una sola vez en la consola y el cambio queda auditado.
 * Solo puede usarlo quien tiene acceso a los ficheros de la base de datos.
 */
public final class PasswordRecovery {
    public static final String OPTION = "--recuperar-acceso";
    static final String ACTOR = "recuperacion-local";

    private PasswordRecovery() { }

    /** @return codigo de salida del proceso (0 si todo fue bien). */
    public static int run(AppConfig config, String username, PrintStream out) {
        Database db = new Database(config.dbUrl(), config.dbUser(), config.dbPassword());
        try {
            db.migrate();
        } catch (PersistenceException e) {
            out.println("No se pudo abrir la base de datos " + config.dbUrl() + ".");
            out.println("Si el backend esta en marcha, detenlo (Ctrl+C) y vuelve a ejecutar esta orden.");
            out.println("Detalle: " + e.getMessage());
            return 1;
        }
        try {
            var accounts = new JdbcUserAccountRepository(db);
            if (username == null || username.isBlank()) {
                List<UserAccount> all = accounts.findAll();
                if (all.isEmpty()) {
                    out.println("No hay cuentas: al abrir la aplicacion web se pedira crear el responsable.");
                    return 0;
                }
                out.println("Cuentas existentes (usuario, perfil, persona):");
                all.forEach(a -> out.printf("  %-20s %-9s %s%n", a.username(), a.role(),
                        a.personId() == null ? "" : a.personId()));
                out.println("Para asignar una clave nueva: " + OPTION + " USUARIO");
                return 0;
            }
            Clock clock = Clock.system(config.zone());
            var service = new AccountAdministrationService(accounts, new JdbcPersonRepository(db),
                    new InMemorySessionRepository(), new Pbkdf2PasswordHasher(config.pbkdf2Iterations()),
                    new JdbcAuditTrail(db, clock), db, clock);
            String password = randomPassword();
            service.resetPassword(username, password.toCharArray(), ACTOR);
            out.println("Clave nueva para '" + UserAccount.normalize(username) + "': " + password);
            out.println("Copiala ahora: no se volvera a mostrar. Puedes cambiarla despues desde la aplicacion");
            out.println("(Personal y centros -> Accesos a la aplicacion).");
            return 0;
        } catch (software.ulpgc.presencia.domain.error.DomainException e) {
            out.println("No se pudo cambiar la clave: " + e.getMessage());
            out.println("Ejecuta " + OPTION + " sin usuario para ver las cuentas existentes.");
            return 1;
        } finally {
            db.shutdown();
        }
    }

    /** 16 caracteres aleatorios (96 bits) en Base64 URL. */
    static String randomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
