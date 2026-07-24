package dev.benjaminor.fluentquery;

import dev.benjaminor.fluentquery.support.Joins;
import dev.benjaminor.fluentquery.support.LikeExpressions;
import dev.benjaminor.fluentquery.support.LikePatterns;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.Attribute;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * Reusable dynamic filters for {@link FluentQuery}.
 * Included automatically when extending {@link FluentQueryRepository} (no second {@code extends}).
 *
 * <p>When used as standalone Specs: {@code null}/blank/empty collection → ignored predicate
 * ({@code null}). Prefer {@code optionalWhere*} on the builder for that behaviour, and strict
 * {@code where*} when the predicate must always apply.
 *
 * <p>LIKE behaviour follows {@link FluentQueryDefaults#likeMode()} ({@link LikeMode#PORTABLE} by
 * default; {@link LikeMode#ORACLE_UNACCENT} is opt-in).
 *
 * @param <T> root entity type
 */
public interface PropertyFilters<T> {

    // ---- LIKE ----

    /**
     * Case-insensitive {@code LIKE}. Blank {@code value} → ignored ({@code null} predicate).
     * If {@code value} contains {@code %} or {@code _}, it is used as a raw pattern; otherwise
     * wrapped as contains ({@code %VALUE%}). See {@link LikePatterns#toPattern(String)}.
     *
     * @param column attribute name
     * @param value  substring or LIKE pattern
     * @return a specification, or an ignored predicate when {@code value} is blank
     */
    default Specification<T> hasPropertyLike(String column, String value) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return cb.like(likeExpression(root, cb, column), LikePatterns.toPattern(value));
        };
    }

    /**
     * Case-insensitive {@code LIKE} on a related attribute (INNER JOIN).
     * Blank {@code value} → ignored. Same pattern rules as {@link #hasPropertyLike(String, String)}.
     *
     * @param relation association name from the root
     * @param column   attribute on the related entity
     * @param value    substring or LIKE pattern
     * @return a specification, or an ignored predicate when {@code value} is blank
     */
    default Specification<T> hasRelatedPropertyLike(String relation, String column, String value) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            var join = Joins.joinPath(root, relation, JoinType.INNER);
            return cb.like(likeExpression(join, cb, column), LikePatterns.toPattern(value));
        };
    }

    /**
     * Case-insensitive {@code LIKE} with escape {@code '\\'} (pattern already escaped / built).
     *
     * @param column  attribute name
     * @param pattern upper-cased pattern (e.g. from {@link LikePatterns#containsEscaped(String)})
     * @return specification
     */
    default Specification<T> hasPropertyLikeEscaped(String column, String pattern) {
        return (root, query, cb) -> cb.like(likeExpression(root, cb, column), pattern, '\\');
    }

    /**
     * Case-insensitive {@code LIKE} using {@code pattern} as-is (trimmed + upper-cased by caller).
     * No escape character — supply any {@code %}/{@code _} yourself.
     *
     * @param column  attribute name
     * @param pattern upper-cased raw LIKE pattern
     * @return specification
     */
    default Specification<T> hasPropertyLikePattern(String column, String pattern) {
        return (root, query, cb) -> cb.like(likeExpression(root, cb, column), pattern);
    }

    // ---- EQUAL / NOT EQUAL ----

    /**
     * Equality. {@code null} value → ignored predicate.
     *
     * @param column attribute name
     * @param value  expected value; {@code null} → ignored
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default Specification<T> hasPropertyEqual(String column, Object value) {
        return (root, query, cb) -> value == null ? null : cb.equal(root.get(column), value);
    }

    /**
     * Inequality. {@code null} value → ignored predicate.
     *
     * @param column attribute name
     * @param value  forbidden value; {@code null} → ignored
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default Specification<T> hasPropertyNotEqual(String column, Object value) {
        return (root, query, cb) -> value == null ? null : cb.notEqual(root.get(column), value);
    }

    /**
     * Equality on a related attribute (INNER JOIN). {@code null} value → ignored.
     *
     * @param relation association name from the root
     * @param column   attribute on the related entity
     * @param value    expected value; {@code null} → ignored
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default Specification<T> hasRelatedPropertyEqual(String relation, String column, Object value) {
        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            var join = Joins.joinPath(root, relation, JoinType.INNER);
            return cb.equal(join.get(column), value);
        };
    }

    // ---- IN / NOT IN ----

    /**
     * {@code IN}. Empty/{@code null} collection → ignored predicate.
     *
     * @param column attribute name
     * @param values allowed values
     * @return a specification, or ignored when empty
     */
    default Specification<T> hasPropertyIn(String column, Collection<?> values) {
        return (root, query, cb) -> {
            if (CollectionUtils.isEmpty(values)) {
                return null;
            }
            return root.get(column).in(values);
        };
    }

    /**
     * {@code NOT IN}. Empty/{@code null} collection → ignored predicate.
     *
     * @param column attribute name
     * @param values forbidden values
     * @return a specification, or ignored when empty
     */
    default Specification<T> hasPropertyNotIn(String column, Collection<?> values) {
        return (root, query, cb) -> {
            if (CollectionUtils.isEmpty(values)) {
                return null;
            }
            return cb.not(root.get(column).in(values));
        };
    }

    // ---- NULL / NOT NULL ----

    /**
     * {@code column IS NULL}.
     *
     * @param column attribute name
     * @return a specification
     */
    default Specification<T> hasPropertyIsNull(String column) {
        return (root, query, cb) -> cb.isNull(root.get(column));
    }

    /**
     * {@code column IS NOT NULL}.
     *
     * @param column attribute name
     * @return a specification
     */
    default Specification<T> hasPropertyIsNotNull(String column) {
        return (root, query, cb) -> cb.isNotNull(root.get(column));
    }

    // ---- COMPARISONS / RANGE ----

    /**
     * {@code column > value}. {@code null} value → ignored.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive lower bound
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default <Y extends Comparable<? super Y>> Specification<T> hasPropertyGreaterThan(String column, Y value) {
        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            return cb.greaterThan(path(root, column), value);
        };
    }

    /**
     * {@code column >= value}. {@code null} value → ignored.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive lower bound
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default <Y extends Comparable<? super Y>> Specification<T> hasPropertyGreaterThanOrEqualTo(String column, Y value) {
        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(path(root, column), value);
        };
    }

    /**
     * {@code column < value}. {@code null} value → ignored.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive upper bound
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default <Y extends Comparable<? super Y>> Specification<T> hasPropertyLessThan(String column, Y value) {
        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            return cb.lessThan(path(root, column), value);
        };
    }

    /**
     * {@code column <= value}. {@code null} value → ignored.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive upper bound
     * @return a specification, or ignored when {@code value} is {@code null}
     */
    default <Y extends Comparable<? super Y>> Specification<T> hasPropertyLessThanOrEqualTo(String column, Y value) {
        return (root, query, cb) -> {
            if (value == null) {
                return null;
            }
            return cb.lessThanOrEqualTo(path(root, column), value);
        };
    }

    /**
     * Inclusive range. If only one bound is present, only that bound is applied.
     * Both {@code null} → ignored predicate.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param from   inclusive lower bound; may be {@code null}
     * @param to     inclusive upper bound; may be {@code null}
     * @return a specification, or ignored when both bounds are {@code null}
     */
    default <Y extends Comparable<? super Y>> Specification<T> hasPropertyBetween(String column, Y from, Y to) {
        return (root, query, cb) -> {
            if (from == null && to == null) {
                return null;
            }
            Path<Y> path = path(root, column);
            if (from != null && to != null) {
                return cb.between(path, from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(path, from);
            }
            return cb.lessThanOrEqualTo(path, to);
        };
    }

    // ---- association existence (Eloquent whereHas / whereDoesntHave) ----

    /**
     * Related association exists: collection → {@code IS NOT EMPTY}; to-one → {@code IS NOT NULL}.
     *
     * @param relation association name on the root entity
     * @return a specification
     */
    default Specification<T> hasRelation(String relation) {
        return (root, query, cb) -> {
            Attribute<?, ?> attr = root.getModel().getAttribute(relation);
            if (attr.isCollection()) {
                return cb.isNotEmpty(root.get(relation));
            }
            return cb.isNotNull(root.get(relation));
        };
    }

    /**
     * Related association absent: collection → {@code IS EMPTY}; to-one → {@code IS NULL}.
     *
     * @param relation association name on the root entity
     * @return a specification
     */
    default Specification<T> hasNoRelation(String relation) {
        return (root, query, cb) -> {
            Attribute<?, ?> attr = root.getModel().getAttribute(relation);
            if (attr.isCollection()) {
                return cb.isEmpty(root.get(relation));
            }
            return cb.isNull(root.get(relation));
        };
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private <Y> Path<Y> path(From<?, ?> from, String column) {
        return (Path<Y>) from.get(column);
    }

    private Expression<String> likeExpression(From<?, ?> from, CriteriaBuilder cb, String column) {
        return LikeExpressions.of(from, cb, column);
    }
}
