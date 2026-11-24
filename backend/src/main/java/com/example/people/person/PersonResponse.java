package com.example.people.person;

import java.time.LocalDate;

public record PersonResponse(Long id, String firstName, String lastName, String email, LocalDate dateOfBirth) {}
