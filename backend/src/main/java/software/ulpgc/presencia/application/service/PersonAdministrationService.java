package software.ulpgc.presencia.application.service;

import software.ulpgc.presencia.application.port.in.PersonAdministrationUseCase;
import software.ulpgc.presencia.application.port.out.AuditTrail;
import software.ulpgc.presencia.application.port.out.PersonRepository;
import software.ulpgc.presencia.application.port.out.Transactions;
import software.ulpgc.presencia.domain.error.PersonAlreadyExistsException;
import software.ulpgc.presencia.domain.person.Person;

import java.util.Comparator;
import java.util.List;

public final class PersonAdministrationService implements PersonAdministrationUseCase {
    private final PersonRepository people;
    private final AuditTrail audit;
    private final Transactions transactions;

    public PersonAdministrationService(PersonRepository people, AuditTrail audit, Transactions transactions) {
        this.people = people;
        this.audit = audit;
        this.transactions = transactions;
    }

    @Override
    public Person register(RegisterPersonCommand command) {
        Person person = new Person(command.id(), command.displayName(), command.team());
        return transactions.inTransaction(() -> {
            if (people.findById(person.id()).isPresent())
                throw new PersonAlreadyExistsException("Ya existe una persona con id '" + person.id() + "'");
            people.save(person);
            audit.record(Actors.normalize(command.actor()), "PERSON_CREATED", "person", person.id(),
                    person.displayName() + (person.team() == null ? "" : " (equipo " + person.team() + ")"));
            return person;
        });
    }

    @Override
    public List<Person> list() {
        return people.findAll().stream().sorted(Comparator.comparing(Person::id)).toList();
    }
}
