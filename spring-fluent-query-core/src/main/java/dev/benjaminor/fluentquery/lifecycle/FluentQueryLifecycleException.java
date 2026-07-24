package dev.benjaminor.fluentquery.lifecycle;

/**
 * Thrown when an {@link EntityLifecycleListener} hook fails.
 *
 * <p>Wraps {@link IllegalStateException} / {@link IllegalArgumentException} from hooks so Spring
 * Data's {@code @Repository} exception translation does not rewrite them as
 * {@code InvalidDataAccessApiUsageException} (those types are reserved for JPA provider errors).
 */
public class FluentQueryLifecycleException extends RuntimeException {

    /**
     * @param message description
     * @param cause   hook failure (never {@code null})
     */
    public FluentQueryLifecycleException(String message, Throwable cause) {
        super(message, cause);
    }
}
