package dev.benjaminor.fluentquery.autoconfigure;

import dev.benjaminor.fluentquery.FluentQueryDefaults;
import dev.benjaminor.fluentquery.LikeMode;
import dev.benjaminor.fluentquery.lifecycle.EntityLifecycleListener;
import dev.benjaminor.fluentquery.lifecycle.EntityLifecycleRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * Auto-configuration for Spring Fluent Query (Boot 3.x and 4.x).
 *
 * <p>Applies {@link SpringFluentQueryProperties} to {@link FluentQueryDefaults} so LIKE behaviour
 * is portable by default and Oracle unaccent is opt-in.
 *
 * <p>Registers {@link EntityLifecycleRegistry} (hooks off unless
 * {@code spring.fluent-query.lifecycle.enabled=true}). Use
 * {@link dev.benjaminor.fluentquery.lifecycle.EnableFluentQueryLifecycle} so repositories are
 * backed by {@link dev.benjaminor.fluentquery.lifecycle.FluentQueryJpaRepository}.
 */
@AutoConfiguration(
        afterName = {
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
        })
@ConditionalOnClass(JpaSpecificationExecutor.class)
@EnableConfigurationProperties(SpringFluentQueryProperties.class)
public class SpringFluentQueryAutoConfiguration {

    /**
     * Applies {@link SpringFluentQueryProperties} to {@link FluentQueryDefaults} at context start.
     *
     * @param properties configuration properties
     * @return initializer bean
     */
    @Bean
    static FluentQueryDefaultsInitializer fluentQueryDefaultsInitializer(
            SpringFluentQueryProperties properties) {
        return new FluentQueryDefaultsInitializer(properties);
    }

    /**
     * Collects all {@link EntityLifecycleListener} beans. Dispatch is gated by
     * {@code spring.fluent-query.lifecycle.enabled} (default {@code false}).
     *
     * @param listeners  optional listener beans
     * @param properties configuration
     * @return registry bean
     */
    @Bean
    @ConditionalOnMissingBean
    EntityLifecycleRegistry entityLifecycleRegistry(
            ObjectProvider<EntityLifecycleListener<?>> listeners,
            SpringFluentQueryProperties properties) {
        List<EntityLifecycleListener<?>> list = listeners.orderedStream().toList();
        boolean enabled = properties.getLifecycle() != null && properties.getLifecycle().isEnabled();
        return new EntityLifecycleRegistry(list, enabled);
    }

    /**
     * Applies configuration once the context starts (and eagerly when the bean is created).
     */
    static final class FluentQueryDefaultsInitializer {

        FluentQueryDefaultsInitializer(SpringFluentQueryProperties properties) {
            LikeMode mode = properties.getLikeMode() != null
                    ? properties.getLikeMode()
                    : LikeMode.PORTABLE;
            FluentQueryDefaults.setLikeMode(mode);
        }
    }
}
