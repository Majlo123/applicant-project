package com.example.people.person;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonService {
    private final PersonRepository repository;
    private final PersonMapper mapper;

    public PersonService(PersonRepository repository, PersonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PersonResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PersonResponse get(long id) { return mapper.toResponse(find(id)); }

    @Transactional
    public PersonResponse create(PersonRequest request) {
        return mapper.toResponse(repository.save(mapper.createEntity(request)));
    }

    @Transactional
    public PersonResponse update(long id, PersonRequest request) {
        Person person = find(id);
        mapper.updateEntity(person, request);
        return mapper.toResponse(person);
    }

    @Transactional
    public void delete(long id) { repository.delete(find(id)); }

    private Person find(long id) {
        return repository.findById(id).orElseThrow(() -> new PersonNotFoundException(id));
    }
}
