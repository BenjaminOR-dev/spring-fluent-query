package dev.benjaminor.fluentquery.lifecycle;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;

/**
 * {@link JpaRepositoryFactory} that builds {@link FluentQueryJpaRepository} with an
 * {@link EntityLifecycleRegistry}.
 */
public class FluentQueryJpaRepositoryFactory extends JpaRepositoryFactory {

    private final EntityLifecycleRegistry lifecycle;

    /**
     * @param entityManager must not be {@code null}
     * @param lifecycle     registry (no-op if {@code null})
     */
    public FluentQueryJpaRepositoryFactory(
            EntityManager entityManager, EntityLifecycleRegistry lifecycle) {
        super(entityManager);
        this.lifecycle = lifecycle != null ? lifecycle : EntityLifecycleRegistry.noop();
    }

    @Override
    protected JpaRepositoryImplementation<?, ?> getTargetRepository(
            RepositoryInformation information, EntityManager entityManager) {
        JpaEntityInformation<?, ?> entityInformation =
                getEntityInformation(information.getDomainType());
        return new FluentQueryJpaRepository<>(entityInformation, entityManager, lifecycle);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return FluentQueryJpaRepository.class;
    }
}
