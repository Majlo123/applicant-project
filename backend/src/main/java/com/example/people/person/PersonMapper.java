package com.example.people.person;

import org.springframework.stereotype.Component;

@Component
public class PersonMapper {
    public Person createEntity(PersonRequest request) {
        Person person = new Person();
        updateEntity(person, request);
        return person;
    }

    /** The single place that copies the four business fields from a request; used by both create and update. */
    public void updateEntity(Person person, PersonRequest request) {
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setEmail(request.email());
        person.setDateOfBirth(request.dateOfBirth());
    }

    public PersonResponse toResponse(Person person) {
        return new PersonResponse(person.getId(), person.getFirstName(), person.getLastName(), person.getEmail(), person.getDateOfBirth());
    }
}
