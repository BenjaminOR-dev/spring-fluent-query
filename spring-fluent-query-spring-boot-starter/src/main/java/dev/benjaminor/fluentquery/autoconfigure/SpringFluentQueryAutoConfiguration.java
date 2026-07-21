package dev.benjaminor.fluentquery.autoconfigure;

import dev.benjaminor.fluentquery.FluentQueryDefaults;
import dev.benjaminor.fluentquery.LikeMode;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Auto-configuration for Spring Fluent Query (Boot 3.x and 4.x).
 *
 * <p>Applies {@link SpringFluentQueryProperties} to {@link FluentQueryDefaults} so LIKE behaviour
 * is portable by default and Oracle unaccent is opt-in.
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
