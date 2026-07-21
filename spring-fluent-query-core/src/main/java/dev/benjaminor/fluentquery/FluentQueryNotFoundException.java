package dev.benjaminor.fluentquery;

/**
 * Thrown by {@link FluentQuery#firstOrFail()} / {@link FluentQuery#oneOrFail()} when no row matches.
 *
 * <p>Optional sugar — hosts can keep using {@link FluentQuery#first()} / {@link FluentQuery#one()}
 * with {@link java.util.Optional} instead.
 */
public class FluentQueryNotFoundException extends RuntimeException {

    /**
     * @param message English detail message
     */
    public FluentQueryNotFoundException(String message) {
        super(message);
    }

    /**
     * @param message English detail message
     * @param cause   underlying cause; may be {@code null}
     */
    public FluentQueryNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
