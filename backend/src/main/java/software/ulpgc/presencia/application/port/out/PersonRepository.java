package software.ulpgc.presencia.application.port.out;

import software.ulpgc.presencia.domain.person.Person;

import java.util.List;
import java.util.Optional;

public interface PersonRepository {
    void save(Person person);

    Optional<Person> findById(String id);

    List<Person> findAll();
}
