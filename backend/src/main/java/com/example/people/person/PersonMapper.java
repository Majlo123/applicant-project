package com.example.people.person;

import org.springframework.stereotype.Component;

@Component
public class PersonMapper {
    public Person createEntity(PersonRequest request) {
        Person person = new Person();
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setEmail(request.email());
        person.setDateOfBirth(request.dateOfBirth());
        return person;
    }

    public void updateEntity(Person person, PersonRequest request) {
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setDateOfBirth(request.dateOfBirth());
    }

    public PersonResponse toResponse(Person person) {
        return new PersonResponse(person.getId(), person.getFirstName(), person.getLastName(), person.getEmail(), person.getDateOfBirth());
    }
}
