package dev.benjaminor.fluentquery.lifecycle;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * Factory bean that wires {@link FluentQueryJpaRepository} with {@link EntityLifecycleRegistry}.
 *
 * <p>Prefer {@link EnableFluentQueryLifecycle} on the application (or configuration) class.
 *
 * @param <R> repository type
 * @param <T> entity type
 * @param <I> id type
 */
public class FluentQueryJpaRepositoryFactoryBean<R extends JpaRepository<T, I>, T, I>
        extends JpaRepositoryFactoryBean<R, T, I> {

    private EntityLifecycleRegistry lifecycle = EntityLifecycleRegistry.noop();

    /**
     * @param repositoryInterface must not be {@code null}
     */
    public FluentQueryJpaRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    /**
     * Injects the starter (or host) {@link EntityLifecycleRegistry} bean when present.
     *
     * @param provider optional registry
     */
    @Autowired
    public void setEntityLifecycleRegistry(ObjectProvider<EntityLifecycleRegistry> provider) {
        this.lifecycle = provider.getIfAvailable(EntityLifecycleRegistry::noop);
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new FluentQueryJpaRepositoryFactory(entityManager, lifecycle);
    }
}
