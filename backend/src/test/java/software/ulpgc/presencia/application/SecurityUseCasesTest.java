package software.ulpgc.presencia.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.ulpgc.presencia.application.port.in.AccountAdministrationUseCase.CreateAccountCommand;
import software.ulpgc.presencia.application.port.in.AuthenticationUseCase.LoginResult;
import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase.RegisterPersonCommand;
import software.ulpgc.presencia.application.security.AccessControl;
import software.ulpgc.presencia.application.security.Principal;
import software.ulpgc.presencia.application.service.AccountAdministrationService;
import software.ulpgc.presencia.application.service.AuthenticationService;
import software.ulpgc.presencia.application.service.PersonAdministrationService;
import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.error.AccountAlreadyExistsException;
import software.ulpgc.presencia.domain.error.AccountNotFoundException;
import software.ulpgc.presencia.domain.error.ForbiddenException;
import software.ulpgc.presencia.domain.error.InvalidCredentialsException;
import software.ulpgc.presencia.domain.error.InvalidDataException;
import software.ulpgc.presencia.domain.error.PersonNotFoundException;
import software.ulpgc.presencia.domain.error.SetupAlreadyDoneException;
import software.ulpgc.presencia.domain.error.UnauthenticatedException;
import software.ulpgc.presencia.support.InMemoryAdapters;
import software.ulpgc.presencia.support.MutableClock;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static software.ulpgc.presencia.domain.Fixtures.*;

/** Inicio de sesion, cuentas y permisos con adaptadores en memoria. */
class SecurityUseCasesTest {
    private MutableClock clock;
    private InMemoryAdapters adapters;
    private AuthenticationService auth;
    private AccountAdministrationService accounts;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(at(MONDAY, 8, 0), ZONE);
        adapters = new InMemoryAdapters(clock);
        var hasher = adapters.passwordHasher();
        auth = new AuthenticationService(adapters.accountRepository(), adapters.personRepository(),
                adapters.sessionRepository(), hasher, adapters.auditTrail(), adapters.transactions(), clock,
                Duration.ofHours(12));
        accounts = new AccountAdministrationService(adapters.accountRepository(), adapters.personRepository(),
                adapters.sessionRepository(), hasher, adapters.auditTrail(), adapters.transactions(), clock);
        new PersonAdministrationService(adapters.personRepository(), adapters.auditTrail(), adapters.transactions())
                .register(new RegisterPersonCommand("EMP-1", "Persona Uno", null, "test"));
    }

    private static char[] pwd(String value) {
        return value.toCharArray();
    }

    @Test
    void theFirstAdminIsCreatedOnlyOnce() {
        assertTrue(auth.setupRequired());
        assertThrows(InvalidDataException.class, () -> auth.setup("jefa", pwd("corta")));
        LoginResult result = auth.setup("Jefa", pwd("clave-segura-1"));
        assertEquals("jefa", result.principal().username(), "el usuario se guarda en minusculas");
        assertTrue(result.principal().isAdmin());
        assertFalse(auth.setupRequired());
        assertThrows(SetupAlreadyDoneException.class, () -> auth.setup("otra", pwd("clave-segura-2")));
        assertEquals("test$clave-segura-1", adapters.accounts.get("jefa").passwordHash(), "solo se guarda el hash");
    }

    @Test
    void loginSessionsExpireAndCanBeClosed() {
        auth.setup("jefa", pwd("clave-segura-1"));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("jefa", pwd("otra-clave")));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("nadie", pwd("otra-clave")));
        LoginResult session = auth.login(" JEFA ", pwd("clave-segura-1"));
        assertEquals("jefa", auth.authenticate(session.token()).username());
        assertThrows(UnauthenticatedException.class, () -> auth.authenticate(null));
        assertThrows(UnauthenticatedException.class, () -> auth.authenticate("inventado"));
        clock.advance(Duration.ofHours(12));
        assertThrows(UnauthenticatedException.class, () -> auth.authenticate(session.token()), "caducada");
        LoginResult other = auth.login("jefa", pwd("clave-segura-1"));
        auth.logout(other.token());
        assertThrows(UnauthenticatedException.class, () -> auth.authenticate(other.token()));
    }

    @Test
    void employeeAccountsBelongToOneExistingPerson() {
        var employee = accounts.create(new CreateAccountCommand("uno", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-1", "jefa"));
        assertEquals("Persona Uno", auth.login("uno", pwd("clave-uno-1")).principal().displayName());
        assertEquals("EMP-1", employee.personId());
        assertThrows(AccountAlreadyExistsException.class, () -> accounts.create(
                new CreateAccountCommand("uno", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-1", "jefa")));
        assertThrows(AccountAlreadyExistsException.class, () -> accounts.create(
                new CreateAccountCommand("otro", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-1", "jefa")),
                "una persona solo tiene una cuenta");
        assertThrows(PersonNotFoundException.class, () -> accounts.create(
                new CreateAccountCommand("tres", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-3", "jefa")));
        assertThrows(InvalidDataException.class, () -> accounts.create(
                new CreateAccountCommand("x", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-1", "jefa")), "usuario corto");
    }

    @Test
    void resettingAPasswordClosesTheSessions() {
        accounts.create(new CreateAccountCommand("uno", pwd("clave-uno-1"), Role.EMPLOYEE, "EMP-1", "jefa"));
        LoginResult session = auth.login("uno", pwd("clave-uno-1"));
        accounts.resetPassword("uno", pwd("clave-nueva-1"), "jefa");
        assertThrows(UnauthenticatedException.class, () -> auth.authenticate(session.token()));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("uno", pwd("clave-uno-1")));
        assertNotNull(auth.login("uno", pwd("clave-nueva-1")).token());
        assertThrows(AccountNotFoundException.class, () -> accounts.resetPassword("nadie", pwd("clave-nueva-1"), "jefa"));
    }

    @Test
    void accessControlRules() {
        Principal admin = new Principal("jefa", Role.ADMIN, null, "Jefa");
        Principal employee = new Principal("uno", Role.EMPLOYEE, "EMP-1", "Persona Uno");
        assertNull(AccessControl.personScope(admin, null), "el responsable puede consultar a todos");
        assertEquals("EMP-2", AccessControl.personScope(admin, "EMP-2"));
        assertEquals("EMP-1", AccessControl.personScope(employee, null), "el empleado siempre es el mismo");
        assertEquals("EMP-1", AccessControl.personScope(employee, "EMP-1"));
        assertThrows(ForbiddenException.class, () -> AccessControl.personScope(employee, "EMP-2"));
        assertThrows(ForbiddenException.class, () -> AccessControl.requireAdmin(employee));
        assertDoesNotThrow(() -> AccessControl.requireAdmin(admin));
    }
}
