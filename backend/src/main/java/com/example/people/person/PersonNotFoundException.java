package com.example.people.person;

public class PersonNotFoundException extends RuntimeException {
    public PersonNotFoundException(long id) {
        super("Person " + id + " was not found");
    }
}
