package software.ulpgc.presencia.infrastructure.http.dto;

import software.ulpgc.presencia.domain.person.Person;

public record PersonDto(String id, String displayName, String team) {
    public static PersonDto from(Person person) {
        return new PersonDto(person.id(), person.displayName(), person.team());
    }
}
