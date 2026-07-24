package dev.benjaminor.fluentquery.lifecycle;

import org.springframework.core.ResolvableType;

/**
 * Base listener that resolves {@link #entityType()} from the concrete generic argument.
 *
 * <pre>{@code
 * @Component
 * class UserLifecycle extends AbstractEntityLifecycleListener<User> {
 *     @Override
 *     public void onCreated(User user) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * @param <T> entity type
 */
public abstract class AbstractEntityLifecycleListener<T> implements EntityLifecycleListener<T> {

    private final Class<T> entityType;

    /**
     * Captures {@code T} from the subclass via {@link ResolvableType}.
     *
     * @throws IllegalStateException if the entity type cannot be resolved
     */
    @SuppressWarnings("unchecked")
    protected AbstractEntityLifecycleListener() {
        ResolvableType type = ResolvableType.forClass(getClass())
                .as(AbstractEntityLifecycleListener.class);
        Class<?> resolved = type.getGeneric(0).resolve();
        if (resolved == null || resolved == Object.class) {
            throw new IllegalStateException(
                    "Cannot resolve entity type for " + getClass().getName()
                            + " — declare a concrete type argument, e.g. "
                            + "AbstractEntityLifecycleListener<User>");
        }
        this.entityType = (Class<T>) resolved;
    }

    @Override
    public final Class<T> entityType() {
        return entityType;
    }
}
