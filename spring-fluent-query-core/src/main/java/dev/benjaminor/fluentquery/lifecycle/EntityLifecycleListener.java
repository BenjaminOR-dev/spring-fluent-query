package dev.benjaminor.fluentquery.lifecycle;

/**
 * Optional Eloquent-style entity lifecycle hooks as a Spring bean (not Active Record).
 *
 * <p>Entities stay POJOs. Register implementations (prefer
 * {@link AbstractEntityLifecycleListener}) and enable
 * {@link FluentQueryJpaRepository} via {@link EnableFluentQueryLifecycle}.
 *
 * <p>Hook order for {@code save}:
 * {@link #onSaving} → {@link #onCreating} <em>or</em> {@link #onUpdating} → persist/merge →
 * {@link #onCreated} <em>or</em> {@link #onUpdated} → {@link #onSaved}.
 *
 * <p>Hook order for {@code delete(entity)}: {@link #onDeleting} → remove → {@link #onDeleted}.
 *
 * <p>Batch JPQL deletes ({@code deleteAllInBatch}, {@code deleteInBatch},
 * {@code deleteAllByIdInBatch}) <b>do not</b> fire hooks.
 *
 * @param <T> entity type this listener applies to (exact type match)
 * @see AbstractEntityLifecycleListener
 * @see EntityLifecycleRegistry
 * @see FluentQueryJpaRepository
 */
public interface EntityLifecycleListener<T> {

    /**
     * Exact entity class this listener handles.
     *
     * @return entity class (never {@code null})
     */
    Class<T> entityType();

    /** Before persist of a new entity (after {@link #onSaving}). */
    default void onCreating(T entity) {
    }

    /** After persist of a new entity (before {@link #onSaved}). */
    default void onCreated(T entity) {
    }

    /** Before merge of an existing entity (after {@link #onSaving}). */
    default void onUpdating(T entity) {
    }

    /** After merge of an existing entity (before {@link #onSaved}). */
    default void onUpdated(T entity) {
    }

    /** Before create <em>or</em> update. */
    default void onSaving(T entity) {
    }

    /** After create <em>or</em> update. */
    default void onSaved(T entity) {
    }

    /** Before remove. */
    default void onDeleting(T entity) {
    }

    /** After remove. */
    default void onDeleted(T entity) {
    }
}
