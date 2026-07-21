package dev.benjaminor.fluentquery.example;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD demo on top of {@link DemoEntityRepository}: writes via {@code save}/{@code delete},
 * reads (and bulk delete) via {@code query()}.
 */
@Service
public class DemoCrudService {

    private final DemoEntityRepository repository;

    public DemoCrudService(DemoEntityRepository repository) {
        this.repository = repository;
    }

    /** Create — persist a new entity ({@code JpaRepository#save}). */
    @Transactional
    public DemoEntity create(Long id, String name) {
        DemoEntity entity = new DemoEntity();
        entity.setId(id);
        entity.setName(name);
        return repository.save(entity);
    }

    /** Read — find one row with Fluent Query. */
    @Transactional(readOnly = true)
    public Optional<DemoEntity> readByName(String name) {
        return repository.query()
                .where("name", name)
                .first();
    }

    /** Read — list with optional search. */
    @Transactional(readOnly = true)
    public List<DemoEntity> search(String nameFragment) {
        return repository.query()
                .optionalWhereLike("name", nameFragment)
                .orderByAsc("id")
                .get();
    }

    /**
     * Update — load with Fluent Query, mutate, {@code save}.
     *
     * @return updated entity, or empty if id was not found
     */
    @Transactional
    public Optional<DemoEntity> updateName(Long id, String newName) {
        return repository.query()
                .where("id", id)
                .first()
                .map(entity -> {
                    entity.setName(newName);
                    return repository.save(entity);
                });
    }

    /**
     * Delete <b>one</b> row by primary key. Load with {@code first()}, then
     * {@code delete(entity)} — do not use bulk {@code delete()} for a single known row.
     *
     * @return {@code true} if a row was deleted
     */
    @Transactional
    public boolean deleteById(Long id) {
        return repository.query()
                .where("id", id)
                .first()
                .map(entity -> {
                    repository.delete(entity);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Delete <b>many</b> rows matching a filter. {@code delete()} removes every match —
     * keep the {@code where*} tight.
     *
     * @return number of deleted rows
     */
    @Transactional
    public long deleteAllByName(String name) {
        return repository.query()
                .where("name", name)
                .delete();
    }
}
