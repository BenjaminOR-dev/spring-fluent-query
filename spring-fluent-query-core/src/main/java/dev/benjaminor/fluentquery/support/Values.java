package dev.benjaminor.fluentquery.support;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Static helpers for “empty” values and text normalisation used by
 * {@link dev.benjaminor.fluentquery.FluentQuery}.
 *
 * <p>Goal: avoid repeating {@code x == null || x.isBlank()} in filters and builders.
 * Does not emulate JavaScript-style emptiness (e.g. {@code Boolean.FALSE} is not empty).
 *
 * <ul>
 *   <li>Input strings → {@link #isBlank(String)}, {@link #trimToNull(String)}, {@link #hasText(String)}</li>
 *   <li>Optional collections / maps → {@link #isEmpty(Collection)}, {@link #isEmpty(Map)}</li>
 *   <li>Nullable references → {@link #isNull(Object)} / {@link #isNotNull(Object)}</li>
 * </ul>
 *
 * @see org.springframework.util.StringUtils
 * @see org.springframework.util.CollectionUtils
 */
public final class Values {

    private Values() {
    }

    // ---- null ----

    /**
     * {@code true} if the reference is {@code null}.
     *
     * @param value any reference
     * @return whether {@code value} is {@code null}
     */
    public static boolean isNull(Object value) {
        return value == null;
    }

    /**
     * {@code true} if the reference is not {@code null}.
     *
     * @param value any reference
     * @return whether {@code value} is non-null
     */
    public static boolean isNotNull(Object value) {
        return value != null;
    }

    // ---- String ----

    /**
     * {@code true} if the string is {@code null}, empty, or only whitespace
     * ({@link String#isBlank()}).
     *
     * @param value maybe-null string
     * @return whether the string is blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Inverse of {@link #isBlank(String)}.
     *
     * @param value maybe-null string
     * @return whether the string has non-whitespace content
     */
    public static boolean hasText(String value) {
        return !isBlank(value);
    }

    /**
     * Trims and converts blank to {@code null}. Useful so optional filters can skip the predicate.
     *
     * @param value maybe-null string
     * @return trimmed text, or {@code null} if blank
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Trims; {@code null} or blank → {@code ""}. Never returns {@code null}.
     *
     * @param value maybe-null string
     * @return trimmed string, never {@code null}
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * {@link #trimToNull(String)} then {@link String#toUpperCase()} (default locale).
     *
     * @param value maybe-null string
     * @return upper-cased trimmed text, or {@code null} if blank
     */
    public static String trimToNullUpper(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    // ---- Collection / Map / array ----

    /**
     * {@code true} if the collection is {@code null} or empty.
     *
     * @param values maybe-null collection
     * @return whether empty
     */
    public static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * Inverse of {@link #isEmpty(Collection)}.
     *
     * @param values maybe-null collection
     * @return whether non-empty
     */
    public static boolean isNotEmpty(Collection<?> values) {
        return !isEmpty(values);
    }

    /**
     * {@code true} if the map is {@code null} or empty.
     *
     * @param values maybe-null map
     * @return whether empty
     */
    public static boolean isEmpty(Map<?, ?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * Inverse of {@link #isEmpty(Map)}.
     *
     * @param values maybe-null map
     * @return whether non-empty
     */
    public static boolean isNotEmpty(Map<?, ?> values) {
        return !isEmpty(values);
    }

    /**
     * {@code true} if the array is {@code null} or length 0.
     *
     * @param values maybe-null array
     * @return whether empty
     */
    public static boolean isEmpty(Object[] values) {
        return values == null || values.length == 0;
    }

    /**
     * Inverse of {@link #isEmpty(Object[])}.
     *
     * @param values maybe-null array
     * @return whether non-empty
     */
    public static boolean isNotEmpty(Object[] values) {
        return !isEmpty(values);
    }

    /**
     * Returns {@code value} when it has text; otherwise throws.
     *
     * @param value   candidate string
     * @param message exception message when blank/{@code null}
     * @return {@code value} unchanged
     * @throws IllegalArgumentException if {@link #isBlank(String)}
     */
    public static String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Returns {@code fallback} when {@code value} is {@code null}.
     *
     * @param <T>      value type
     * @param value    maybe-null value
     * @param fallback non-null fallback
     * @return {@code value} or {@code fallback}
     * @throws NullPointerException if {@code fallback} is {@code null}
     */
    public static <T> T defaultIfNull(T value, T fallback) {
        return value != null ? value : Objects.requireNonNull(fallback, "fallback");
    }
}
