package dev.benjaminor.fluentquery.autoconfigure;

import dev.benjaminor.fluentquery.LikeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration for {@code spring.fluent-query.*}.
 *
 * <p>Bound at startup by {@link SpringFluentQueryAutoConfiguration} into
 * {@link dev.benjaminor.fluentquery.FluentQueryDefaults} (LIKE) and
 * {@link dev.benjaminor.fluentquery.lifecycle.EntityLifecycleRegistry} (hooks).
 */
@ConfigurationProperties(prefix = "spring.fluent-query")
public class SpringFluentQueryProperties {

    /**
     * LIKE strategy. Default {@link LikeMode#PORTABLE} (JPA {@code UPPER} + {@code LIKE}).
     * Set {@link LikeMode#ORACLE_UNACCENT} for Oracle {@code CONVERT(..., 'US7ASCII')}.
     */
    private LikeMode likeMode = LikeMode.PORTABLE;

    /**
     * Optional entity lifecycle hooks (Eloquent-style). Disabled by default.
     */
    @NestedConfigurationProperty
    private final Lifecycle lifecycle = new Lifecycle();

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

    /**
     * @return lifecycle settings
     */
    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    /**
     * {@code spring.fluent-query.lifecycle.*}.
     */
    public static class Lifecycle {

        /**
         * When {@code true}, {@link dev.benjaminor.fluentquery.lifecycle.EntityLifecycleRegistry}
         * dispatches listener callbacks. Repositories must also use
         * {@link dev.benjaminor.fluentquery.lifecycle.FluentQueryJpaRepository} (see
         * {@link dev.benjaminor.fluentquery.lifecycle.EnableFluentQueryLifecycle}).
         */
        private boolean enabled = false;

        /**
         * @return whether lifecycle hooks are dispatched
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether lifecycle hooks are dispatched
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
