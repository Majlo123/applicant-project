package com.example.people.person;

import com.example.people.DatabaseTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonApiSmokeIT extends DatabaseTestSupport {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepareData() { jdbc.execute("TRUNCATE TABLE person RESTART IDENTITY"); }

    @Test
    void createsReadsListsAndDeletesAnOrdinaryPerson() {
        var request = new PersonRequest("Anna", "Novak", "anna@example.com", LocalDate.of(1994, 5, 12));
        var created = http.postForEntity("/api/persons", request, PersonResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/persons/1");
        assertThat(created.getBody().id()).isEqualTo(1L);
        var read = http.getForEntity("/api/persons/1", PersonResponse.class);
        assertThat(read.getBody()).isEqualTo(created.getBody());
        assertThat(http.getForObject("/api/persons", PersonResponse[].class)).containsExactly(created.getBody());
        var deleted = http.exchange("/api/persons/1", HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleted.getBody()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person", Long.class)).isZero();
    }

    @Test
    void malformedInputIsRejected() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.postForEntity("/api/persons", new HttpEntity<>("{invalid", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("INVALID_REQUEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM person", Long.class)).isZero();
    }
}
