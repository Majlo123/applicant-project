package com.example.people.person;

import java.time.LocalDate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private final PersonRepository repository;
    private final PersonMapper mapper;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(PersonRepository repository, PersonMapper mapper, JdbcTemplate jdbc) {
        this.repository = repository;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() != 0 || Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_called FROM person_id_seq", Boolean.class))) return;
        add("Anna", "Novak", "anna.novak@example.com", "1994-05-12");
        add("Daniel", "Meyer", "daniel.meyer@example.com", "1988-09-23");
        add("Sara", "Ali", "sara.ali@example.com", "2000-02-07");
    }

    private void add(String firstName, String lastName, String email, String birthday) {
        repository.save(mapper.createEntity(new PersonRequest(firstName, lastName, email, LocalDate.parse(birthday))));
    }
}
