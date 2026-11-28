package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.AuthenticationUseCase;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.PasswordHasher;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.SessionRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.application.port.out.UserAccountRepository;
import software.ulpgc.presencia.application.security.Principal;
import software.ulpgc.presencia.application.security.Session;
import software.ulpgc.presencia.domain.account.PasswordPolicy;
import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;
import software.ulpgc.presencia.domain.error.DomainException;
import software.ulpgc.presencia.domain.error.ErrorCode;
import software.ulpgc.presencia.domain.person.Person;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Inicio de sesion con usuario y clave. Cada sesion es un token aleatorio de 256 bits que
 * caduca a las {@code sessionDuration}. Los mensajes de error no revelan si el usuario existe.
 */
public final class AuthenticationService implements AuthenticationUseCase {
    private final UserAccountRepository accounts;
    private final PersonRepository people;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final AuditTrail audit;
    private final Transactions transactions;
    private final Clock clock;
    private final Duration sessionDuration;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthenticationService(UserAccountRepository accounts, PersonRepository people, SessionRepository sessions,
                                 PasswordHasher hasher, AuditTrail audit, Transactions transactions, Clock clock,
                                 Duration sessionDuration) {
        this.accounts = accounts;
        this.people = people;
        this.sessions = sessions;
        this.hasher = hasher;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.sessionDuration = sessionDuration;
        this.dummyHash = hasher.hash("clave-ficticia-para-igualar-tiempos".toCharArray());
    }

    @Override
    public boolean setupRequired() {
        return !accounts.existsWithRole(Role.ADMIN);
    }

    @Override
    public synchronized LoginResult setup(String username, char[] password) {
        try {
            PasswordPolicy.check(password);
            UserAccount account = transactions.inTransaction(() -> {
                if (!setupRequired())
                    throw new DomainException(ErrorCode.SETUP_ALREADY_DONE, "Ya existe una cuenta de responsable; inicia sesion");
                UserAccount created = new UserAccount(username, Role.ADMIN, null, hasher.hash(password), clock.instant());
                accounts.save(created);
                audit.record(created.username(), "ACCOUNT_CREATED", "account", created.username(),
                        "Primera cuenta de responsable");
                return created;
            });
            return open(account);
        } finally {
            clear(password);
        }
    }

    @Override
    public LoginResult login(String username, char[] password) {
        try {
            if (username == null || username.isBlank() || password == null || password.length == 0)
                throw new DomainException(ErrorCode.INVALID_DATA, "Indica usuario y clave");
            Optional<UserAccount> account = accounts.findByUsername(username.strip().toLowerCase());
            // Se calcula el hash aunque el usuario no exista para no revelarlo por el tiempo de respuesta.
            boolean valid = hasher.matches(password, account.map(UserAccount::passwordHash).orElse(dummyHash));
            if (account.isEmpty() || !valid) throw new DomainException(ErrorCode.INVALID_CREDENTIALS, "Usuario o clave incorrectos");
            return open(account.get());
        } finally {
            clear(password);
        }
    }

    @Override
    public void logout(String token) {
        if (token != null) sessions.delete(token);
    }

    @Override
    public Principal authenticate(String token) {
        if (token == null || token.isBlank()) throw new DomainException(ErrorCode.UNAUTHENTICATED, "Inicia sesion para continuar");
        Session session = sessions.find(token)
                .orElseThrow(() -> new DomainException(ErrorCode.UNAUTHENTICATED, "La sesion no es valida o ha caducado"));
        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.delete(token);
            throw new DomainException(ErrorCode.UNAUTHENTICATED, "La sesion ha caducado; vuelve a iniciar sesion");
        }
        UserAccount account = accounts.findByUsername(session.username()).orElseThrow(() -> {
            sessions.delete(token);
            return new DomainException(ErrorCode.UNAUTHENTICATED, "La cuenta ya no existe");
        });
        return principal(account);
    }

    private LoginResult open(UserAccount account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(sessionDuration);
        sessions.save(new Session(token, account.username(), expiresAt));
        return new LoginResult(token, expiresAt, principal(account));
    }

    private Principal principal(UserAccount account) {
        String displayName = account.personId() == null ? account.username()
                : people.findById(account.personId()).map(Person::displayName).orElse(account.username());
        return new Principal(account.username(), account.role(), account.personId(), displayName);
    }

    private static void clear(char[] password) {
        if (password != null) Arrays.fill(password, '\0');
    }
}
