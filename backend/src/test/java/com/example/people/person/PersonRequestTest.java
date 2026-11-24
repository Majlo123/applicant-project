package com.example.people.person;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import static org.assertj.core.api.Assertions.assertThat;

class PersonRequestTest {
    private static final LocalDate BIRTHDAY = LocalDate.of(1994, 5, 12);
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        // Same rule as the application: "today" is determined in UTC.
        factory = Validation.byDefaultProvider().configure().clockProvider(() -> Clock.systemUTC()).buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() { factory.close(); }

    @Test
    void normalizesTextBeforeValidation() {
        var request = new PersonRequest("  Anna  ", " Novak ", " Anna@example.com ", BIRTHDAY);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.firstName()).isEqualTo("Anna");
        assertThat(request.email()).isEqualTo("Anna@example.com");
    }

    @Test
    void trimsUnicodeWhitespaceAroundTextFields() {
        String nbsp = cp(0x00A0), ideographicSpace = cp(0x3000), emSpace = cp(0x2003), bom = cp(0xFEFF);
        var request = new PersonRequest(nbsp + "Zoë" + ideographicSpace, "\t" + emSpace + "Novak\n", bom + " Anna.Novak@Example.COM" + nbsp, BIRTHDAY);
        assertThat(request.firstName()).isEqualTo("Zoë");
        assertThat(request.lastName()).isEqualTo("Novak");
        assertThat(request.email()).isEqualTo("Anna.Novak@Example.COM");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void treatsUnicodeWhitespaceOnlyValuesAsMissing() {
        String blank = cp(0x00A0, 0x3000, 0x2003);
        var errors = errors(new PersonRequest(blank, blank, blank, null));
        assertThat(errors.get("firstName")).containsExactly("Enter a first name");
        assertThat(errors.get("lastName")).containsExactly("Enter a last name");
        // The email format rule also fails on an empty value; the API reports the "required" message first.
        assertThat(errors.get("email")).contains("Enter an email address");
        assertThat(errors.get("dateOfBirth")).containsExactly("Enter a date of birth");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Zoë", "O'Brien", "D’Angelo", "Anne-Marie", "José María", "Đorđe", "Łukasz", "Nguyễn", "李小龍", "Ψυχή"})
    void acceptsUnicodeNamesWithSpacesApostrophesAndHyphens(String name) {
        assertThat(errors(new PersonRequest(name, name, "anna@example.com", BIRTHDAY))).isEmpty();
    }

    @Test
    void acceptsNamesWrittenWithCombiningMarks() {
        String decomposed = "Zoe" + cp(0x0308);
        assertThat(errors(new PersonRequest(decomposed, "Novak", "anna@example.com", BIRTHDAY))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Edit1", "Anna!", "<b>Anna</b>", "Anna_Novak", "Anna.Maria"})
    void rejectsDigitsAndSymbolsInNames(String name) {
        assertThat(errors(new PersonRequest(name, "Novak", "anna@example.com", BIRTHDAY)))
                .isEqualTo(Map.of("firstName", Set.of("Use letters, spaces, apostrophes and hyphens only")));
    }

    @Test
    void countsNameLengthInCharactersRatherThanUtf16Units() {
        String supplementaryLetter = cp(0x20000); // one character, two UTF-16 code units
        assertThat(errors(new PersonRequest(supplementaryLetter.repeat(100), "Novak", "anna@example.com", BIRTHDAY))).isEmpty();
        assertThat(errors(new PersonRequest(supplementaryLetter.repeat(101), "Novak", "anna@example.com", BIRTHDAY)))
                .isEqualTo(Map.of("firstName", Set.of("Use 100 characters or fewer")));
    }

    @Test
    void countsEmailLengthInCharactersRatherThanUtf16Units() {
        String supplementaryLetter = cp(0x20000);
        String domain = "@example.com"; // 12 characters
        assertThat(errors(new PersonRequest("Anna", "Novak", supplementaryLetter.repeat(242) + domain, BIRTHDAY))).isEmpty();
        assertThat(errors(new PersonRequest("Anna", "Novak", supplementaryLetter.repeat(243) + domain, BIRTHDAY)))
                .isEqualTo(Map.of("email", Set.of("Use 254 characters or fewer")));
    }

    @Test
    void rejectsEmailsWithUnicodeWhitespaceInside() {
        for (String email : new String[] {"anna" + cp(0x00A0) + "novak@example.com", "anna@exam" + cp(0x2003) + "ple.com", "anna novak@example.com"}) {
            assertThat(errors(new PersonRequest("Anna", "Novak", email, BIRTHDAY)))
                    .isEqualTo(Map.of("email", Set.of("Enter a valid email address")));
        }
    }

    @Test
    void acceptsTodayInUtcAndRejectsTomorrow() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(errors(new PersonRequest("Anna", "Novak", "anna@example.com", today))).isEmpty();
        assertThat(errors(new PersonRequest("Anna", "Novak", "anna@example.com", today.plusDays(1))))
                .isEqualTo(Map.of("dateOfBirth", Set.of("Date of birth cannot be in the future")));
    }

    private static Map<String, Set<String>> errors(PersonRequest request) {
        Map<String, Set<String>> errors = new TreeMap<>();
        validator.validate(request).forEach(violation -> errors
                .computeIfAbsent(violation.getPropertyPath().toString(), key -> new TreeSet<>())
                .add(violation.getMessage()));
        return errors;
    }

    private static String cp(int... codePoints) { return new String(codePoints, 0, codePoints.length); }
}
