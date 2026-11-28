package software.ulpgc.presencia.support;

import software.ulpgc.presencia.application.port.out.AssignmentRepository;
import software.ulpgc.presencia.application.port.out.PasswordHasher;
import software.ulpgc.presencia.application.port.out.SessionRepository;
import software.ulpgc.presencia.application.port.out.UserAccountRepository;
import software.ulpgc.presencia.application.port.out.WorkplaceRepository;
import software.ulpgc.presencia.application.security.Session;
import software.ulpgc.presencia.domain.account.Role;
import software.ulpgc.presencia.domain.account.UserAccount;
import software.ulpgc.presencia.domain.workplace.Workplace;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.ClockEventRepository;
import software.ulpgc.presencia.application.port.out.HolidayRepository;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.ScheduleRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.domain.assignment.ScheduleAssignment;
import software.ulpgc.presencia.domain.audit.AuditEntry;
import software.ulpgc.presencia.domain.calendar.Holiday;
import software.ulpgc.presencia.domain.clocking.ClockEvent;
import software.ulpgc.presencia.domain.person.Person;
import software.ulpgc.presencia.domain.schedule.Schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adaptadores de salida en memoria. Demuestran que los casos de uso dependen solo de los
 * puertos: las mismas pruebas podrian ejecutarse contra los adaptadores JDBC.
 */
public final class InMemoryAdapters {
    public final Map<String, Person> people = new LinkedHashMap<>();
    public final Map<String, Schedule> schedules = new LinkedHashMap<>();
    public final List<ScheduleAssignment> assignments = new ArrayList<>();
    public final Map<LocalDate, Holiday> holidays = new LinkedHashMap<>();
    public final List<ClockEvent> events = new ArrayList<>();
    public final List<AuditEntry> audit = new ArrayList<>();
    public final Map<String, Workplace> workplaces = new LinkedHashMap<>();
    public final Map<String, UserAccount> accounts = new LinkedHashMap<>();
    public final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Clock clock;

    public InMemoryAdapters(Clock clock) {
        this.clock = clock;
    }

    public PersonRepository personRepository() {
        return new PersonRepository() {
            public void save(Person p) { people.put(p.id(), p); }
            public Optional<Person> findById(String id) { return Optional.ofNullable(people.get(id)); }
            public List<Person> findAll() { return List.copyOf(people.values()); }
        };
    }

    public ScheduleRepository scheduleRepository() {
        return new ScheduleRepository() {
            public void save(Schedule s) { schedules.put(s.id(), s); }
            public Optional<Schedule> findById(String id) { return Optional.ofNullable(schedules.get(id)); }
            public List<Schedule> findAll() { return List.copyOf(schedules.values()); }
        };
    }

    public AssignmentRepository assignmentRepository() {
        return new AssignmentRepository() {
            public void save(ScheduleAssignment a) { assignments.add(a); }
            public List<ScheduleAssignment> findAll() { return List.copyOf(assignments); }
        };
    }

    public HolidayRepository holidayRepository() {
        return new HolidayRepository() {
            public void save(Holiday h) { holidays.put(h.date(), h); }
            public Optional<Holiday> findByDate(LocalDate d) { return Optional.ofNullable(holidays.get(d)); }
            public List<Holiday> findAll() { return List.copyOf(holidays.values()); }
        };
    }

    public ClockEventRepository clockEventRepository() {
        return new ClockEventRepository() {
            public void append(ClockEvent e) { events.add(e); }

            public Optional<ClockEvent> findLast(String personId) {
                return events.stream().filter(e -> e.personId().equals(personId))
                        .max(Comparator.comparing(ClockEvent::occurredAt).thenComparing(ClockEvent::recordedAt));
            }

            public List<ClockEvent> find(String personId, Instant from, Instant to) {
                return events.stream()
                        .filter(e -> personId == null || e.personId().equals(personId))
                        .filter(e -> !e.occurredAt().isBefore(from) && e.occurredAt().isBefore(to))
                        .sorted(Comparator.comparing(ClockEvent::occurredAt)).toList();
            }
        };
    }

    public AuditTrail auditTrail() {
        return new AuditTrail() {
            public void record(String actor, String action, String type, String id, String detail) {
                audit.add(new AuditEntry(audit.size() + 1, clock.instant(), actor, action, type, id, detail));
            }

            public List<AuditEntry> recent(int limit) {
                return audit.reversed().stream().limit(limit).toList();
            }
        };
    }

    public WorkplaceRepository workplaceRepository() {
        return new WorkplaceRepository() {
            public void save(Workplace w) { workplaces.put(w.id(), w); }
            public Optional<Workplace> findById(String id) { return Optional.ofNullable(workplaces.get(id)); }
            public List<Workplace> findAll() { return List.copyOf(workplaces.values()); }
        };
    }

    public UserAccountRepository accountRepository() {
        return new UserAccountRepository() {
            public void save(UserAccount a) { accounts.put(a.username(), a); }
            public void updatePassword(String u, String h) { accounts.computeIfPresent(u, (k, a) -> a.withPasswordHash(h)); }
            public Optional<UserAccount> findByUsername(String u) { return Optional.ofNullable(accounts.get(u)); }
            public List<UserAccount> findAll() { return List.copyOf(accounts.values()); }
            public boolean existsWithRole(Role role) { return accounts.values().stream().anyMatch(a -> a.role() == role); }
        };
    }

    public SessionRepository sessionRepository() {
        return new SessionRepository() {
            public void save(Session s) { sessions.put(s.token(), s); }
            public Optional<Session> find(String t) { return Optional.ofNullable(sessions.get(t)); }
            public void delete(String t) { sessions.remove(t); }
            public void deleteByUsername(String u) { sessions.values().removeIf(s -> s.username().equals(u)); }
        };
    }

    /** Hash de prueba (no seguro): suficiente para comprobar los casos de uso. */
    public PasswordHasher passwordHasher() {
        return new PasswordHasher() {
            public String hash(char[] p) { return "test$" + new String(p); }
            public boolean matches(char[] p, String h) { return h.equals("test$" + new String(p)); }
        };
    }

    /** Sin transacciones reales: suficiente para pruebas de un solo hilo. */
    public Transactions transactions() {
        return new Transactions() {
            public <T> T inTransaction(Supplier<T> work) { return work.get(); }
        };
    }
}
