package dev.benjaminor.fluentquery.lifecycle;

import org.springframework.data.util.ProxyUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Resolves {@link EntityLifecycleListener} beans for an entity class and dispatches hooks.
 *
 * <p>Matching is <b>exact</b> on {@link EntityLifecycleListener#entityType()} (after unwrapping
 * Hibernate/Spring proxies). Listeners for a superclass do not apply to subclasses.
 *
 * <p>When {@link #isEnabled()} is {@code false}, all fire methods are no-ops.
 */
public final class EntityLifecycleRegistry {

    private final boolean enabled;
    private final Map<Class<?>, List<EntityLifecycleListener<?>>> byType;

    /**
     * @param listeners listeners to index (may be empty); {@code null} treated as empty
     * @param enabled   when {@code false}, dispatch is skipped
     */
    public EntityLifecycleRegistry(
            List<? extends EntityLifecycleListener<?>> listeners, boolean enabled) {
        this.enabled = enabled;
        Map<Class<?>, List<EntityLifecycleListener<?>>> map = new LinkedHashMap<>();
        if (listeners != null) {
            for (EntityLifecycleListener<?> listener : listeners) {
                Objects.requireNonNull(listener, "listener");
                Class<?> type = Objects.requireNonNull(
                        listener.entityType(), "listener.entityType()");
                map.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
            }
        }
        Map<Class<?>, List<EntityLifecycleListener<?>>> frozen = new LinkedHashMap<>();
        for (Map.Entry<Class<?>, List<EntityLifecycleListener<?>>> e : map.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.byType = Collections.unmodifiableMap(frozen);
    }

    /**
     * Disabled registry with no listeners (safe default for factory injection).
     *
     * @return no-op registry
     */
    public static EntityLifecycleRegistry noop() {
        return new EntityLifecycleRegistry(List.of(), false);
    }

    /**
     * @return whether hooks are dispatched
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Listeners registered for the exact entity class (no inheritance walk).
     *
     * @param entityClass entity class
     * @param <T>         entity type
     * @return immutable list (possibly empty)
     */
    @SuppressWarnings("unchecked")
    public <T> List<EntityLifecycleListener<T>> listenersFor(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass");
        List<EntityLifecycleListener<?>> found = byType.get(entityClass);
        if (found == null || found.isEmpty()) {
            return List.of();
        }
        return (List) found;
    }

    /** @see EntityLifecycleListener#onSaving(Object) */
    public <T> void fireOnSaving(T entity) {
        fire(entity, EntityLifecycleListener::onSaving);
    }

    /** @see EntityLifecycleListener#onCreating(Object) */
    public <T> void fireOnCreating(T entity) {
        fire(entity, EntityLifecycleListener::onCreating);
    }

    /** @see EntityLifecycleListener#onCreated(Object) */
    public <T> void fireOnCreated(T entity) {
        fire(entity, EntityLifecycleListener::onCreated);
    }

    /** @see EntityLifecycleListener#onUpdating(Object) */
    public <T> void fireOnUpdating(T entity) {
        fire(entity, EntityLifecycleListener::onUpdating);
    }

    /** @see EntityLifecycleListener#onUpdated(Object) */
    public <T> void fireOnUpdated(T entity) {
        fire(entity, EntityLifecycleListener::onUpdated);
    }

    /** @see EntityLifecycleListener#onSaved(Object) */
    public <T> void fireOnSaved(T entity) {
        fire(entity, EntityLifecycleListener::onSaved);
    }

    /** @see EntityLifecycleListener#onDeleting(Object) */
    public <T> void fireOnDeleting(T entity) {
        fire(entity, EntityLifecycleListener::onDeleting);
    }

    /** @see EntityLifecycleListener#onDeleted(Object) */
    public <T> void fireOnDeleted(T entity) {
        fire(entity, EntityLifecycleListener::onDeleted);
    }

    @SuppressWarnings("unchecked")
    private <T> void fire(T entity, BiConsumer<EntityLifecycleListener<T>, T> action) {
        if (!enabled || entity == null) {
            return;
        }
        Class<?> type = ProxyUtils.getUserClass(entity);
        for (EntityLifecycleListener<?> listener : listenersFor(type)) {
            try {
                action.accept((EntityLifecycleListener<T>) listener, entity);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                // Spring @Repository translation maps these to InvalidDataAccessApiUsageException;
                // rethrow as a dedicated type so domain/hook failures stay distinguishable.
                throw new FluentQueryLifecycleException(
                        "Entity lifecycle hook failed for " + type.getName()
                                + " (listener " + listener.getClass().getName() + ")",
                        ex);
            }
        }
    }
}