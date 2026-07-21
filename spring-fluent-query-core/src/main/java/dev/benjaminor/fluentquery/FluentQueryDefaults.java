package dev.benjaminor.fluentquery;

/**
 * Process-wide defaults for {@link FluentQuery} (set by the Spring Boot starter, or manually in
 * tests).
 *
 * <p>Thread-safe. Prefer configuration property
 * {@code spring.fluent-query.like-mode=portable|oracle-unaccent} over calling setters in
 * application code.
 */
public final class FluentQueryDefaults {

    private static volatile LikeMode likeMode = LikeMode.PORTABLE;

    private FluentQueryDefaults() {
    }

    /**
     * Current LIKE strategy (default {@link LikeMode#PORTABLE}).
     *
     * @return active like mode
     */
    public static LikeMode likeMode() {
        return likeMode;
    }

    /**
     * Sets the process-wide LIKE strategy.
     *
     * @param mode new mode; {@code null} resets to {@link LikeMode#PORTABLE}
     */
    public static void setLikeMode(LikeMode mode) {
        likeMode = mode != null ? mode : LikeMode.PORTABLE;
    }

    /**
     * Restores factory defaults (intended for tests).
     */
    public static void reset() {
        likeMode = LikeMode.PORTABLE;
    }
}
