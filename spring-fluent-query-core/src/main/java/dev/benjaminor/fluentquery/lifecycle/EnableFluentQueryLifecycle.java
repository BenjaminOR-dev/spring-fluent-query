package dev.benjaminor.fluentquery.lifecycle;

import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.core.annotation.AliasFor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.data.repository.query.QueryLookupStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables JPA repositories backed by {@link FluentQueryJpaRepository} so optional entity lifecycle
 * hooks can fire.
 *
 * <p>Also set {@code spring.fluent-query.lifecycle.enabled=true} (starter) so the
 * {@link EntityLifecycleRegistry} actually dispatches listener callbacks.
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableFluentQueryLifecycle
 * public class Application { }
 * }</pre>
 *
 * <p>Equivalent to:
 * <pre>{@code
 * @EnableJpaRepositories(
 *     repositoryFactoryBeanClass = FluentQueryJpaRepositoryFactoryBean.class,
 *     repositoryBaseClass = FluentQueryJpaRepository.class)
 * }</pre>
 *
 * @see FluentQueryJpaRepositoryFactoryBean
 * @see EntityLifecycleListener
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnableJpaRepositories(
        repositoryFactoryBeanClass = FluentQueryJpaRepositoryFactoryBean.class,
        repositoryBaseClass = FluentQueryJpaRepository.class)
public @interface EnableFluentQueryLifecycle {

    /** @see EnableJpaRepositories#value() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] value() default {};

    /** @see EnableJpaRepositories#basePackages() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String[] basePackages() default {};

    /** @see EnableJpaRepositories#basePackageClasses() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?>[] basePackageClasses() default {};

    /** @see EnableJpaRepositories#includeFilters() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] includeFilters() default {};

    /** @see EnableJpaRepositories#excludeFilters() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    Filter[] excludeFilters() default {};

    /** @see EnableJpaRepositories#repositoryImplementationPostfix() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String repositoryImplementationPostfix() default "Impl";

    /** @see EnableJpaRepositories#namedQueriesLocation() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String namedQueriesLocation() default "";

    /** @see EnableJpaRepositories#queryLookupStrategy() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    QueryLookupStrategy.Key queryLookupStrategy() default QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND;

    /** @see EnableJpaRepositories#repositoryFactoryBeanClass() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?> repositoryFactoryBeanClass() default FluentQueryJpaRepositoryFactoryBean.class;

    /** @see EnableJpaRepositories#repositoryBaseClass() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    Class<?> repositoryBaseClass() default FluentQueryJpaRepository.class;

    /** @see EnableJpaRepositories#considerNestedRepositories() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean considerNestedRepositories() default false;

    /** @see EnableJpaRepositories#entityManagerFactoryRef() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String entityManagerFactoryRef() default "entityManagerFactory";

    /** @see EnableJpaRepositories#transactionManagerRef() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    String transactionManagerRef() default "transactionManager";

    /** @see EnableJpaRepositories#bootstrapMode() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    BootstrapMode bootstrapMode() default BootstrapMode.DEFAULT;

    /** @see EnableJpaRepositories#enableDefaultTransactions() */
    @AliasFor(annotation = EnableJpaRepositories.class)
    boolean enableDefaultTransactions() default true;
}
