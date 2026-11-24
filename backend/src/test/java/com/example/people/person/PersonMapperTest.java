package com.example.people.person;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class PersonMapperTest {
    private final PersonMapper mapper = new PersonMapper();
    private final PersonRequest anna = new PersonRequest("Anna", "Novak", "anna@example.com", LocalDate.of(1994, 5, 12));
    private final PersonRequest zoe = new PersonRequest("Zoë", "O'Brien", "zoe@example.org", LocalDate.of(1980, 2, 29));

    @Test
    void createCopiesAllFourBusinessFields() {
        Person person = mapper.createEntity(anna);
        assertThat(person.getId()).isNull();
        assertThat(mapper.toResponse(person)).isEqualTo(new PersonResponse(null, "Anna", "Novak", "anna@example.com", LocalDate.of(1994, 5, 12)));
    }

    @Test
    void updateReplacesAllFourBusinessFields() {
        Person person = mapper.createEntity(anna);
        mapper.updateEntity(person, zoe);
        assertThat(mapper.toResponse(person)).isEqualTo(new PersonResponse(null, "Zoë", "O'Brien", "zoe@example.org", LocalDate.of(1980, 2, 29)));
    }
}
