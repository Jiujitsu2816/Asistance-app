package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.AccountAdministrationUseCase;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.PasswordHasher;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.SessionRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.application.port.out.UserAccountRepository;
import software.ulpgc.presencia.domain.account.PasswordPolicy;
import software.ulpgc.presencia.domain.account.UserAccount;
import software.ulpgc.presencia.domain.error.AccountAlreadyExistsException;
import software.ulpgc.presencia.domain.error.AccountNotFoundException;
import software.ulpgc.presencia.domain.error.PersonNotFoundException;

import java.time.Clock;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class AccountAdministrationService implements AccountAdministrationUseCase {
    private final UserAccountRepository accounts;
    private final PersonRepository people;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final AuditTrail audit;
    private final Transactions transactions;
    private final Clock clock;

    public AccountAdministrationService(UserAccountRepository accounts, PersonRepository people,
                                        SessionRepository sessions, PasswordHasher hasher, AuditTrail audit,
                                        Transactions transactions, Clock clock) {
        this.accounts = accounts;
        this.people = people;
        this.sessions = sessions;
        this.hasher = hasher;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public UserAccount create(CreateAccountCommand command) {
        try {
            PasswordPolicy.check(command.password());
            UserAccount account = new UserAccount(command.username(), command.role(), command.personId(),
                    hasher.hash(command.password()), clock.instant());
            return transactions.inTransaction(() -> {
                if (accounts.findByUsername(account.username()).isPresent())
                    throw new AccountAlreadyExistsException("Ya existe el usuario '" + account.username() + "'");
                if (account.personId() != null) {
                    if (people.findById(account.personId()).isEmpty())
                        throw new PersonNotFoundException("No existe ninguna persona con id '" + account.personId() + "'");
                    if (accounts.findAll().stream().anyMatch(a -> account.personId().equals(a.personId())))
                        throw new AccountAlreadyExistsException("La persona '" + account.personId() + "' ya tiene cuenta");
                }
                accounts.save(account);
                audit.record(Actors.normalize(command.actor()), "ACCOUNT_CREATED", "account", account.username(),
                        account.role() + (account.personId() == null ? "" : " para " + account.personId()));
                return account;
            });
        } finally {
            if (command.password() != null) Arrays.fill(command.password(), '\0');
        }
    }

    @Override
    public List<UserAccount> list() {
        return accounts.findAll().stream().sorted(Comparator.comparing(UserAccount::username)).toList();
    }

    @Override
    public void resetPassword(String username, char[] password, String actor) {
        try {
            PasswordPolicy.check(password);
            String name = UserAccount.normalize(username);
            String hash = hasher.hash(password);
            transactions.inTransaction(() -> {
                if (accounts.findByUsername(name).isEmpty())
                    throw new AccountNotFoundException("No existe el usuario '" + name + "'");
                accounts.updatePassword(name, hash);
                audit.record(Actors.normalize(actor), "PASSWORD_RESET", "account", name, "Clave cambiada");
                return null;
            });
            sessions.deleteByUsername(name);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }
}
