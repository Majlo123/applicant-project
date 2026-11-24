package com.example.people.person;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/persons")
public class PersonController {
    private final PersonService service;

    public PersonController(PersonService service) { this.service = service; }

    @GetMapping
    public List<PersonResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    public PersonResponse get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody PersonRequest request) {
        PersonResponse saved = service.create(request);
        return ResponseEntity.created(URI.create("/api/persons/" + saved.id())).body(saved);
    }

    @PutMapping("/{id}")
    public PersonResponse update(@PathVariable long id, @Valid @RequestBody PersonRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
