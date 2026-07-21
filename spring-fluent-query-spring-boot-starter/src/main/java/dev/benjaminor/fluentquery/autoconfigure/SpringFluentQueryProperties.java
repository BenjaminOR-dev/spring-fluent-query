package dev.benjaminor.fluentquery.autoconfigure;

import dev.benjaminor.fluentquery.LikeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@code spring.fluent-query.*}.
 *
 * <p>Bound at startup by {@link SpringFluentQueryAutoConfiguration} into
 * {@link dev.benjaminor.fluentquery.FluentQueryDefaults}.
 */
@ConfigurationProperties(prefix = "spring.fluent-query")
public class SpringFluentQueryProperties {

    /**
     * LIKE strategy. Default {@link LikeMode#PORTABLE} (JPA {@code UPPER} + {@code LIKE}).
     * Set {@link LikeMode#ORACLE_UNACCENT} for Oracle {@code CONVERT(..., 'US7ASCII')}.
     */
    private LikeMode likeMode = LikeMode.PORTABLE;

    /**
     * @return configured LIKE strategy
     */
    public LikeMode getLikeMode() {
        return likeMode;
    }

    /**
     * @param likeMode LIKE strategy to apply at startup
     */
    public void setLikeMode(LikeMode likeMode) {
        this.likeMode = likeMode;
    }
}
