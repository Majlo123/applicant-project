package com.example.people.person;

import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.CodePointLength;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.people.shared.IsoDateDeserializer;
import java.time.LocalDate;

public record PersonRequest(
        @NotBlank(message = "Enter a first name")
        @CodePointLength(max = 100, message = "Use 100 characters or fewer")
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String firstName,
        @NotBlank(message = "Enter a last name")
        @CodePointLength(max = 100, message = "Use 100 characters or fewer")
        @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String lastName,
        @NotBlank(message = "Enter an email address")
        @CodePointLength(max = 254, message = "Use 254 characters or fewer")
        @Pattern(regexp = "(?U)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "Enter a valid email address") String email,
        @NotNull(message = "Enter a date of birth")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
        @JsonDeserialize(using = IsoDateDeserializer.class)
        @PastOrPresent(message = "Date of birth cannot be in the future") LocalDate dateOfBirth) {
    // Letters and combining marks from any script, space separators, apostrophes (' and U+2019) and hyphens (- and U+2010).
    // An empty value is left to @NotBlank, so a blank name reports only "Enter a ... name".
    static final String NAME_PATTERN = "^[\\p{L}\\p{M}\\p{Zs}'\\u2019\\-\\u2010]*$";
    static final String NAME_MESSAGE = "Use letters, spaces, apostrophes and hyphens only";

    public PersonRequest {
        firstName = trim(firstName);
        lastName = trim(lastName);
        email = trim(email);
    }

    /** Removes surrounding Unicode whitespace (and control characters, as String.trim() did). */
    private static String trim(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end && isTrimmed(value.charAt(start))) start++;
        while (end > start && isTrimmed(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean isTrimmed(char character) {
        return Character.isSpaceChar(character) || Character.isISOControl(character) || character == 0xFEFF;
    }
}
