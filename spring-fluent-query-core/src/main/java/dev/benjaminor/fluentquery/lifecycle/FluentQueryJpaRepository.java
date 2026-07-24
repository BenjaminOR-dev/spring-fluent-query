package dev.benjaminor.fluentquery.lifecycle;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.util.ProxyUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SimpleJpaRepository} that dispatches optional {@link EntityLifecycleListener} hooks.
 *
 * <p><b>Not Active Record</b> — entities remain POJOs; hooks live on Spring beans collected by
 * {@link EntityLifecycleRegistry}.
 *
 * <p>Enable via {@link EnableFluentQueryLifecycle} (or
 * {@code @EnableJpaRepositories(repositoryFactoryBeanClass = FluentQueryJpaRepositoryFactoryBean.class)})
 * and {@code spring.fluent-query.lifecycle.enabled=true}.
 *
 * <p><b>Hook coverage:</b>
 * <ul>
 *   <li>{@link #save} / {@link #saveAll} — full creating/updating/saving chain</li>
 *   <li>{@link #delete} / {@link #deleteAll} / {@link #deleteById} / {@link #deleteAllById} /
 *       {@link #deleteAll()} — per-entity deleting/deleted ({@code deleteById} already loads then
 *       delegates to {@link #delete})</li>
 *   <li>{@link #deleteAllInBatch}, {@link #deleteInBatch}, {@link #deleteAllByIdInBatch} —
 *       <b>skip</b> lifecycle hooks (Eloquent-style mass delete)</li>
 * </ul>
 *
 * <p>Pre-hooks run inside the same {@code @Transactional} boundary as persist/merge/remove: a
 * runtime exception from {@code onCreating} / {@code onDeleting} (etc.) aborts the operation and
 * marks the transaction rollback-only when invoked through the Spring repository proxy.
 *
 * @param <T>  entity type
 * @param <ID> id type
 * @see EntityLifecycleListener
 * @see FluentQueryJpaRepositoryFactoryBean
 */
public class FluentQueryJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private static final String ENTITY_MUST_NOT_BE_NULL = "Entity must not be null";
    private static final String ENTITIES_MUST_NOT_BE_NULL = "Entities must not be null";

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;
    private final EntityLifecycleRegistry lifecycle;

    /**
     * @param entityInformation must not be {@code null}
     * @param entityManager     must not be {@code null}
     * @param lifecycle         registry (use {@link EntityLifecycleRegistry#noop()} if unused)
     */
    public FluentQueryJpaRepository(
            JpaEntityInformation<T, ?> entityInformation,
            EntityManager entityManager,
            EntityLifecycleRegistry lifecycle) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.lifecycle = lifecycle != null ? lifecycle : EntityLifecycleRegistry.noop();
    }

    /**
     * Two-arg constructor for Spring Data reflection when only {@code repositoryBaseClass} is set.
     * Uses a no-op registry — prefer {@link FluentQueryJpaRepositoryFactoryBean} so the real
     * registry is injected.
     *
     * @param entityInformation must not be {@code null}
     * @param entityManager     must not be {@code null}
     */
    public FluentQueryJpaRepository(
            JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        this(entityInformation, entityManager, EntityLifecycleRegistry.noop());
    }

    @Override
    @Transactional
    public <S extends T> S save(S entity) {
        Assert.notNull(entity, ENTITY_MUST_NOT_BE_NULL);

        // Snapshot before pre-hooks so persist vs merge stays aligned with creating/updating
        // even if a listener mutates identity fields.
        boolean isNew = entityInformation.isNew(entity);
        lifecycle.fireOnSaving(entity);
        if (isNew) {
            lifecycle.fireOnCreating(entity);
        } else {
            lifecycle.fireOnUpdating(entity);
        }

        final S saved;
        if (isNew) {
            entityManager.persist(entity);
            saved = entity;
        } else {
            saved = entityManager.merge(entity);
        }

        if (isNew) {
            lifecycle.fireOnCreated(saved);
        } else {
            lifecycle.fireOnUpdated(saved);
        }
        lifecycle.fireOnSaved(saved);
        return saved;
    }

    @Override
    @Transactional
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        Assert.notNull(entities, ENTITIES_MUST_NOT_BE_NULL);
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void delete(T entity) {
        Assert.notNull(entity, ENTITY_MUST_NOT_BE_NULL);

        if (entityInformation.isNew(entity)) {
            return;
        }

        // Mirror SimpleJpaRepository.delete: missing entities are a NOOP — do not fire hooks.
        if (entityManager.contains(entity)) {
            lifecycle.fireOnDeleting(entity);
            entityManager.remove(entity);
            lifecycle.fireOnDeleted(entity);
            return;
        }

        Class<?> type = ProxyUtils.getUserClass(entity);
        T existing = (T) entityManager.find(type, entityInformation.getId(entity));
        if (existing == null) {
            return;
        }

        lifecycle.fireOnDeleting(entity);
        entityManager.remove(entityManager.merge(entity));
        lifecycle.fireOnDeleted(entity);
    }

    /*
     * deleteById / deleteAll / deleteAllById already delegate to delete(entity) in
     * SimpleJpaRepository — hooks fire automatically.
     *
     * deleteAllInBatch / deleteInBatch / deleteAllByIdInBatch intentionally NOT overridden:
     * batch JPQL deletes skip lifecycle hooks (documented).
     */
}
