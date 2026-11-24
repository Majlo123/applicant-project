package com.example.people.person;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.people.shared.IsoDateDeserializer;
import java.time.LocalDate;

public record PersonRequest(
        @NotBlank(message = "Enter a first name")
        @Size(max = 100, message = "Use 100 characters or fewer") String firstName,
        @NotBlank(message = "Enter a last name")
        @Size(max = 100, message = "Use 100 characters or fewer") String lastName,
        @NotBlank(message = "Enter an email address")
        @Size(max = 254, message = "Use 254 characters or fewer")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Enter a valid email address") String email,
        @NotNull(message = "Enter a date of birth")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        @JsonDeserialize(using = IsoDateDeserializer.class)
        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth) {
    public PersonRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        email = trim(email);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
