package software.ulpgc.presencia.application.port.in;

import software.ulpgc.presencia.domain.person.Person;

import java.util.List;

public interface PersonAdministrationUseCase {
    record RegisterPersonCommand(String id, String displayName, String team, String actor) { }

    Person register(RegisterPersonCommand command);

    List<Person> list();
}
