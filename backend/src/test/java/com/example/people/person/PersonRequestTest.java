package com.example.people.person;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class PersonRequestTest {
    @Test
    void normalizesTextBeforeValidation() {
        var request = new PersonRequest("  Anna  ", " Novak ", " Anna@example.com ", LocalDate.of(1994, 5, 12));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        assertThat(request.firstName()).isEqualTo("Anna");
        assertThat(request.email()).isEqualTo("Anna@example.com");
    }
}
