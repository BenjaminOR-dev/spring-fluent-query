package dev.benjaminor.fluentquery.support;

import dev.benjaminor.fluentquery.RelatedFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.Attribute;

import java.util.function.Consumer;

/**
 * LEFT JOIN FETCH helpers: reuse an existing fetch on the same association and
 * walk dotted paths ({@code "profile.address"}).
 *
 * <p>Constrained eager loads (Eloquent {@code with} + closure) use {@link Join#on(Predicate)}
 * on the outermost fetch so filters apply to the association without turning the load into an
 * inner-join filter on the root (Hibernate exposes {@link Fetch} as a {@link Join}).
 */
public final class Fetches {

    private Fetches() {
    }

    /**
     * Returns an existing fetch for {@code relation} on {@code parent}, or creates a new one.
     *
     * @param parent   root or intermediate fetch parent
     * @param relation association attribute name (single segment)
     * @param joinType used only when creating a new fetch
     * @return existing or newly created fetch
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Fetch<?, ?> reuseOrCreate(FetchParent<?, ?> parent, String relation, JoinType joinType) {
        for (Fetch<?, ?> existing : parent.getFetches()) {
            Attribute<?, ?> attr = existing.getAttribute();
            if (attr != null
                    && relation.equals(attr.getName())
                    && existing.getJoinType() == joinType) {
                return existing;
            }
        }
        return parent.fetch(relation, joinType);
    }

    /**
     * Applies LEFT JOIN FETCH for a possibly dotted path, reusing intermediate fetches.
     *
     * <p>Examples: {@code "company"}, {@code "company.address"}.
     *
     * @param root     root entity
     * @param path     attribute path; blank → no-op
     * @param joinType join type for each segment
     * @return the outermost fetch, or {@code null} if {@code path} is blank
     */
    public static Fetch<?, ?> fetchPath(FetchParent<?, ?> root, String path, JoinType joinType) {
        if (!Values.hasText(path)) {
            return null;
        }
        String[] segments = path.trim().split("\\.");
        FetchParent<?, ?> current = root;
        Fetch<?, ?> last = null;
        for (String segment : segments) {
            if (!Values.hasText(segment)) {
                throw new IllegalArgumentException("fetch path contains a blank segment: '" + path + "'");
            }
            last = reuseOrCreate(current, segment.trim(), joinType);
            current = last;
        }
        return last;
    }

    /**
     * LEFT JOIN FETCH {@code path} and optionally apply Eloquent-style constraints via
     * {@link Join#on(Predicate)} on the leaf association.
     *
     * @param root        root entity
     * @param cb          criteria builder
     * @param path        association path
     * @param joinType    join type (typically {@link JoinType#LEFT})
     * @param constraints related predicates; {@code null} or empty → plain fetch
     * @return outermost fetch, or {@code null} if path blank
     * @throws IllegalStateException if constraints are present but the fetch is not a {@link Join}
     */
    public static Fetch<?, ?> fetchPathConstrained(
            FetchParent<?, ?> root,
            CriteriaBuilder cb,
            String path,
            JoinType joinType,
            Consumer<RelatedFilter> constraints) {
        Fetch<?, ?> fetch = fetchPath(root, path, joinType);
        if (fetch == null || constraints == null) {
            return fetch;
        }
        RelatedFilter filter = new RelatedFilter();
        constraints.accept(filter);
        if (filter.isEmpty()) {
            return fetch;
        }
        applyOn(fetch, cb, filter);
        return fetch;
    }

    /**
     * Applies {@code filter} as an {@code ON} clause on a fetch that is also a {@link Join}.
     *
     * @param fetch  fetch from {@link #fetchPath}
     * @param cb     criteria builder
     * @param filter non-empty related filter
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void applyOn(Fetch<?, ?> fetch, CriteriaBuilder cb, RelatedFilter filter) {
        if (!(fetch instanceof Join<?, ?> join)) {
            throw new IllegalStateException(
                    "FluentQuery.with(..., constraints) requires the JPA provider to expose "
                            + "JOIN FETCH as a Join (supported on Hibernate / Spring Data JPA).");
        }
        Predicate on = filter.toPredicate(join, cb);
        join.on(on);
    }
}
