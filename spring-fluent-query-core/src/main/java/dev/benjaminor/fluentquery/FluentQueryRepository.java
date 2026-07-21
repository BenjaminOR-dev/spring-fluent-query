package dev.benjaminor.fluentquery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * The only interface the host should extend: CRUD + Specifications + rich filters + {@link #query()}.
 *
 * <pre>{@code
 * public interface UserRepository extends FluentQueryRepository&lt;User, Long&gt; {
 * }
 *
 * userRepository.query().where("email", email).latest("createdAt");
 * }</pre>
 *
 * <p>Includes {@link PropertyFilters}. LIKE defaults to {@link LikeMode#PORTABLE}
 * ({@code spring.fluent-query.like-mode}); set {@code oracle-unaccent} for Oracle CONVERT.
 *
 * @param <T>  entity
 * @param <ID> id type
 */
@NoRepositoryBean
public interface FluentQueryRepository<T, ID>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T>, PropertyFilters<T> {

    /**
     * Starts a {@link FluentQuery} with rich filters already wired.
     *
     * @return a new single-shot fluent query builder
     */
    default FluentQuery<T> query() {
        return FluentQuery.of(this, this);
    }
}
