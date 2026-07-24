package dev.benjaminor.fluentquery;

import dev.benjaminor.fluentquery.support.Fetches;
import dev.benjaminor.fluentquery.support.Joins;
import dev.benjaminor.fluentquery.support.LikeExpressions;
import dev.benjaminor.fluentquery.support.LikePatterns;
import dev.benjaminor.fluentquery.support.SelectPaths;
import dev.benjaminor.fluentquery.support.Values;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Fluent query builder over {@link JpaSpecificationExecutor} — Eloquent-style DX without Active Record.
 *
 * <p><b>Stability contract (v1):</b> this class is a <em>predicate composer</em>;
 * terminal execution delegates to Spring Data's official fluent API
 * ({@code executor.findBy(spec, q -> …)}) to avoid unnecessary COUNT queries in
 * {@link #first()} / {@link #latest(String)} and to align projection / slice / stream.
 *
 * <p><b>Usage (single-shot, not thread-safe):</b>
 * <pre>{@code
 * userRepository.query()
 *     .where("email", email)
 *     .fetch("company")                    // to-one: OK with page
 *     .fetch("profile.address")            // nested to-one path
 *     .latest("createdAt");                // LIMIT 1, no COUNT
 * }</pre>
 *
 * <p><b>Performance rules:</b>
 * <ul>
 *   <li>{@link #fetch(String...)} — <b>to-one</b> associations ({@code @ManyToOne}/{@code @OneToOne}),
 *       including dotted paths ({@code "a.b.c"}); intermediate fetches are reused</li>
 *   <li>{@link #fetchCollection(String...)} — to-many; <b>forbidden</b> with {@link #page(Pageable)}</li>
 *   <li>{@link #get()} without {@link #limit(int)} may load the whole table — prefer limit/page/slice</li>
 * </ul>
 *
 * @param <T> root JPA entity type
 * @see JpaSpecificationExecutor#findBy(Specification, java.util.function.Function)
 */
public final class FluentQuery<T> {

    private final JpaSpecificationExecutor<T> executor;
    /** Optional rich filters (e.g. Oracle LIKE); {@code null} = portable mode. */
    private final PropertyFilters<T> filters;

    private Specification<T> spec = null;
    private Sort sort = Sort.unsorted();
    private Integer limit = null;
    private boolean distinct = false;
    private final List<String> fetchToOne = new ArrayList<>();
    private final List<String> fetchCollections = new ArrayList<>();
    /**
     * Eloquent-style {@code ON} constraints for eager loads ({@link #with}/{@link #withCollection}).
     * Key = association path; applied on the leaf join of that path.
     */
    private final java.util.Map<String, Consumer<RelatedFilter>> fetchOnConstraints =
            new java.util.LinkedHashMap<>();
    /** Property paths for Spring Data {@code project(...)} (Eloquent-style {@code select}). */
    private final List<String> selectColumns = new ArrayList<>();
    /** Set after a terminal executes; builder must not be reused. */
    private boolean consumed = false;

    private FluentQuery(JpaSpecificationExecutor<T> executor, PropertyFilters<T> filters) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.filters = filters;
    }

    /**
     * Portable mode: any {@link JpaSpecificationExecutor} (no rich {@link PropertyFilters}).
     *
     * @param <T>      entity type
     * @param executor Spring Data specification executor
     * @return a new single-shot builder
     */
    public static <T> FluentQuery<T> of(JpaSpecificationExecutor<T> executor) {
        return new FluentQuery<>(executor, null);
    }

    /**
     * Builder with rich filters (e.g. a repository that implements {@link PropertyFilters}).
     *
     * @param <T>      entity type
     * @param executor Spring Data specification executor
     * @param filters  rich property filters; may be {@code null} for portable mode
     * @return a new single-shot builder
     */
    public static <T> FluentQuery<T> of(
            JpaSpecificationExecutor<T> executor, PropertyFilters<T> filters) {
        return new FluentQuery<>(executor, filters);
    }

    // -------------------------------------------------------------------------
    // where (strict) — always applies the predicate
    // optionalWhere* — only when a value is present (null/blank/empty collection → no-op)
    // whereIf / when — conditional on a boolean (distinct from optional*)
    // -------------------------------------------------------------------------

    /**
     * AND with a typed {@link Specification} (scopes). {@code null} is ignored.
     *
     * @param specification predicate to AND; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> where(Specification<T> specification) {
        ensureOpen();
        if (specification == null) {
            return this;
        }
        this.spec = this.spec == null ? specification : this.spec.and(specification);
        return this;
    }

    /**
     * Strict equality: {@code where("celular", value)}.
     * {@code null} → {@code IS NULL}. To skip when there is no value, use {@link #optionalWhere}.
     *
     * @param column attribute name on the root entity
     * @param value  expected value; {@code null} means {@code IS NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> where(String column, Object value) {
        return whereEqual(column, value);
    }

    /**
     * AND group: {@code where(q -> q.where("a",1).where("b",2))}.
     *
     * @param group nested builder consumer; nested predicates are AND-ed as a group
     * @return {@code this} for chaining
     */
    public FluentQuery<T> where(Consumer<FluentQuery<T>> group) {
        Specification<T> nested = buildGroupSpec(group);
        return nested == null ? this : where(nested);
    }

    /**
     * Applies {@code specification} only when {@code condition} is {@code true}.
     * Unlike {@code optionalWhere*}, this is driven by an explicit boolean, not value presence.
     *
     * @param condition     when {@code false}, no-op
     * @param specification predicate to AND when condition holds
     * @return {@code this} for chaining
     * @see #when(boolean, Consumer)
     */
    public FluentQuery<T> whereIf(boolean condition, Specification<T> specification) {
        return condition ? where(specification) : this;
    }

    /**
     * Applies strict equality only when {@code condition} is {@code true}.
     *
     * @param condition when {@code false}, no-op
     * @param column    attribute name
     * @param value     expected value; {@code null} → {@code IS NULL} when applied
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereIf(boolean condition, String column, Object value) {
        return condition ? where(column, value) : this;
    }

    /**
     * Runs {@code consumer} on {@code this} only when {@code condition} is {@code true}.
     * Useful to batch several filters behind one boolean (Eloquent {@code when}).
     *
     * <pre>{@code
     * query().when(hasSearch, q -> q.optionalWhereLike("name", search));
     * }</pre>
     *
     * @param condition when {@code false}, no-op
     * @param consumer  mutations to apply; {@code null} is ignored
     * @return {@code this} for chaining
     * @see #when(boolean, Consumer, Consumer)
     * @see #unless(boolean, Consumer)
     * @see #whereIf(boolean, Specification)
     */
    public FluentQuery<T> when(boolean condition, Consumer<FluentQuery<T>> consumer) {
        if (condition && consumer != null) {
            consumer.accept(this);
        }
        return this;
    }

    /**
     * Eloquent-style {@code when} with an else branch: runs {@code thenConsumer} when
     * {@code condition} is {@code true}, otherwise {@code elseConsumer}.
     *
     * <pre>{@code
     * query().when(includeInactive,
     *         q -> q.whereIn("status", List.of("ACTIVE", "INACTIVE")),
     *         q -> q.where("status", "ACTIVE"));
     * }</pre>
     *
     * @param condition     branch selector
     * @param thenConsumer  applied when {@code condition} is {@code true}; {@code null} ignored
     * @param elseConsumer  applied when {@code condition} is {@code false}; {@code null} ignored
     * @return {@code this} for chaining
     * @see #when(boolean, Consumer)
     */
    public FluentQuery<T> when(
            boolean condition,
            Consumer<FluentQuery<T>> thenConsumer,
            Consumer<FluentQuery<T>> elseConsumer) {
        if (condition) {
            if (thenConsumer != null) {
                thenConsumer.accept(this);
            }
        } else if (elseConsumer != null) {
            elseConsumer.accept(this);
        }
        return this;
    }

    /**
     * Runs {@code consumer} on {@code this} only when {@code condition} is {@code false}.
     * Equivalent to {@code when(!condition, consumer)}.
     *
     * @param condition when {@code true}, no-op
     * @param consumer  mutations to apply; {@code null} is ignored
     * @return {@code this} for chaining
     * @see #when(boolean, Consumer)
     */
    public FluentQuery<T> unless(boolean condition, Consumer<FluentQuery<T>> consumer) {
        return when(!condition, consumer);
    }

    /**
     * OR with a predicate. Semantics: {@code (accumulated filters) OR (new)}.
     * To group: {@link #orWhere(Consumer)}.
     *
     * @param specification predicate to OR; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhere(Specification<T> specification) {
        ensureOpen();
        if (specification == null) {
            return this;
        }
        this.spec = this.spec == null ? specification : this.spec.or(specification);
        return this;
    }

    /**
     * Strict OR equality. {@code null} → {@code IS NULL}. See {@link #optionalOrWhere}.
     *
     * @param column attribute name
     * @param value  expected value; {@code null} means {@code IS NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhere(String column, Object value) {
        if (value == null) {
            return orWhere((root, query, cb) -> cb.isNull(root.get(column)));
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        return orWhere(strictEqualSpec(column, normalized));
    }

    /**
     * OR group: {@code orWhere(q -> q.where("a",1).where("b",2))} → {@code … OR (a AND b)}.
     *
     * @param group nested builder consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhere(Consumer<FluentQuery<T>> group) {
        Specification<T> nested = buildGroupSpec(group);
        return nested == null ? this : orWhere(nested);
    }


    /**
     * Strict OR {@code LIKE} (non-blank). Same pattern rules as {@link #whereLike(String, String)}.
     *
     * @param column attribute name
     * @param value  substring or pattern; must not be blank
     * @return {@code this} for chaining
     * @see #optionalOrWhereLike(String, String)
     */
    public FluentQuery<T> orWhereLike(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalOrWhereLike for search params");
        return orWhere(likeSpec(column, value));
    }

    /**
     * Strict OR escaped contains {@code LIKE}.
     *
     * @param column attribute name
     * @param value  free-text substring; must not be blank
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereContains(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereContains for search params");
        return orWhere(likeEscapedSpec(column, LikePatterns.containsEscaped(value)));
    }

    /**
     * Strict OR escaped prefix {@code LIKE}.
     *
     * @param column attribute name
     * @param value  prefix; must not be blank
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereStartsWith(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereStartsWith for search params");
        return orWhere(likeEscapedSpec(column, LikePatterns.startsWithEscaped(value)));
    }

    /**
     * Strict OR escaped suffix {@code LIKE}.
     *
     * @param column attribute name
     * @param value  suffix; must not be blank
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereEndsWith(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereEndsWith for search params");
        return orWhere(likeEscapedSpec(column, LikePatterns.endsWithEscaped(value)));
    }

    /**
     * Strict OR raw {@code LIKE} pattern (stripped + upper-cased with {@link java.util.Locale#ROOT}).
     *
     * @param column  attribute name
     * @param pattern raw pattern; must not be blank
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereLikePattern(String column, String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        Values.requireText(pattern, "pattern must not be blank; use optionalWhereLikePattern for search params");
        return orWhere(likeRawSpec(column, Values.trimToEmpty(pattern).toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Strict OR {@code IN}. Empty collection → never-matches ({@code disjunction}).
     *
     * @param column attribute name
     * @param values allowed values; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereIn(String column, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        return orWhere(inSpec(column, values));
    }

    /**
     * Strict OR {@code NOT IN}. Empty collection → always-true ({@code conjunction}).
     *
     * @param column attribute name
     * @param values forbidden values; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereNotIn(String column, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        return orWhere(notInSpec(column, values));
    }

    /**
     * AND of the negation of {@code specification}. {@code null} is ignored.
     *
     * @param specification predicate to negate and AND
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNot(Specification<T> specification) {
        if (specification == null) {
            return this;
        }
        return where(Specification.not(specification));
    }

    /**
     * Strict equality. {@code null} → {@link #whereNull}.
     *
     * @param column attribute name
     * @param value  expected value; {@code null} means {@code IS NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereEqual(String column, Object value) {
        if (value == null) {
            return whereNull(column);
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        return where(strictEqualSpec(column, normalized));
    }

    /**
     * Case-insensitive equality on a string column ({@code UPPER(col) = UPPER(value)}),
     * using {@link java.util.Locale#ROOT} for the Java-side normalisation (DB {@code UPPER}
     * remains vendor-defined). {@code null} → {@link #whereNull(String)}.
     *
     * @param column attribute name
     * @param value  expected value; blank after trim → equal to blank / empty after UPPER
     * @return {@code this} for chaining
     * @see #optionalWhereEqualIgnoreCase(String, String)
     */
    public FluentQuery<T> whereEqualIgnoreCase(String column, String value) {
        Objects.requireNonNull(column, "column");
        Values.requireText(column, "column must not be blank (string attribute only)");
        if (value == null) {
            return whereNull(column);
        }
        String normalized = Values.trimToNullUpper(value);
        return where(equalIgnoreCaseSpec(column, normalized == null ? "" : normalized));
    }

    /**
     * Applies {@link #whereEqual} only when {@code condition} is {@code true}.
     *
     * @param condition when {@code false}, no-op
     * @param column    attribute name
     * @param value     expected value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereEqualIf(boolean condition, String column, Object value) {
        return condition ? whereEqual(column, value) : this;
    }

    /**
     * Strict inequality. {@code null} → {@link #whereNotNull}.
     *
     * @param column attribute name
     * @param value  forbidden value; {@code null} means {@code IS NOT NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNotEqual(String column, Object value) {
        if (value == null) {
            return whereNotNull(column);
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        return where(strictNotEqualSpec(column, normalized));
    }

    /**
     * Equality on a related entity attribute via INNER JOIN.
     * {@code null} → {@code related.column IS NULL} on rows where the association
     * <em>exists</em> (INNER). Missing associations are excluded — use
     * {@link #whereDoesntHave(String)} / {@link #whereHas(String)} for presence, not dotted
     * {@code where("a.b", v)} (unsupported by design).
     *
     * @param relation association name or dotted path from the root
     * @param column   attribute on the related entity
     * @param value    expected value; {@code null} means {@code IS NULL} on the join path
     * @return {@code this} for chaining
     * @see #whereRelation(String, String, Object)
     */
    public FluentQuery<T> whereRelatedEqual(String relation, String column, Object value) {
        if (value == null) {
            return where((root, query, cb) -> {
                Join<?, ?> join = Joins.joinPath(root, relation, JoinType.INNER);
                return cb.isNull(join.get(column));
            });
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        return where((root, query, cb) -> {
            Join<?, ?> join = Joins.joinPath(root, relation, JoinType.INNER);
            return cb.equal(join.get(column), normalized);
        });
    }

    /**
     * Case-insensitive {@code LIKE} (strict; non-blank value required).
     *
     * <p>Pattern rules ({@link LikePatterns#toPattern(String)}):
     * <ul>
     *   <li>No {@code %}/{@code _} in {@code value} → contains match {@code %VALUE%}</li>
     *   <li>Value already has {@code %} or {@code _} → used as a raw pattern (e.g. {@code "ADA%"},
     *       {@code "%ADA"}, {@code "_X%"})</li>
     * </ul>
     *
     * <p>For free-text search where {@code %} must be literal, prefer {@link #whereContains},
     * {@link #whereStartsWith}, or {@link #whereEndsWith} (wildcards are escaped).
     *
     * @param column attribute name
     * @param value  substring or LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     * @see #optionalWhereLike(String, String)
     * @see #whereLikePattern(String, String)
     */
    public FluentQuery<T> whereLike(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereLike for search params");
        return where(likeSpec(column, value));
    }

    /**
     * Case-insensitive contains match; {@code %} / {@code _} in {@code value} are escaped
     * (literal). Prefer for free-text UI search.
     *
     * @param column attribute name
     * @param value  free-text substring; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public FluentQuery<T> whereContains(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereContains for search params");
        return where(likeEscapedSpec(column, LikePatterns.containsEscaped(value)));
    }

    /**
     * Case-insensitive prefix match ({@code VALUE%}); wildcards in {@code value} are escaped.
     *
     * @param column attribute name
     * @param value  prefix; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public FluentQuery<T> whereStartsWith(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereStartsWith for search params");
        return where(likeEscapedSpec(column, LikePatterns.startsWithEscaped(value)));
    }

    /**
     * Case-insensitive suffix match ({@code %VALUE}); wildcards in {@code value} are escaped.
     *
     * @param column attribute name
     * @param value  suffix; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public FluentQuery<T> whereEndsWith(String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereEndsWith for search params");
        return where(likeEscapedSpec(column, LikePatterns.endsWithEscaped(value)));
    }

    /**
     * Case-insensitive {@code LIKE} using {@code pattern} as-is (only trimmed + upper-cased).
     * You must supply any {@code %}/{@code _} yourself.
     *
     * @param column  attribute name
     * @param pattern raw LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if {@code pattern} is blank
     */
    public FluentQuery<T> whereLikePattern(String column, String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        Values.requireText(pattern, "pattern must not be blank; use optionalWhereLikePattern for search params");
        return where(likeRawSpec(column, Values.trimToEmpty(pattern).toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Case-insensitive {@code LIKE} on a related attribute (INNER JOIN).
     * Same pattern rules as {@link #whereLike(String, String)} ({@link LikePatterns#toPattern(String)}).
     *
     * @param relation association name from the root
     * @param column   attribute on the related entity
     * @param value    substring or LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     * @see #optionalWhereRelatedLike(String, String, String)
     */
    public FluentQuery<T> whereRelatedLike(String relation, String column, String value) {
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereRelatedLike for search params");
        return where(relatedLikeSpec(relation, column, value));
    }

    /**
     * Strict {@code IN}. Empty collection → a predicate that never matches ({@code disjunction});
     * the filter is <em>not</em> omitted. See {@link #optionalWhereIn}.
     *
     * @param column attribute name
     * @param values allowed values; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public FluentQuery<T> whereIn(String column, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return where((root, query, cb) -> cb.disjunction());
        }
        return where(inSpec(column, values));
    }

    /**
     * Strict {@code NOT IN}. Empty collection → always-true ({@code conjunction});
     * the filter is <em>not</em> omitted. See {@link #optionalWhereNotIn}.
     *
     * @param column attribute name
     * @param values forbidden values; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public FluentQuery<T> whereNotIn(String column, Collection<?> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            return where((root, query, cb) -> cb.conjunction());
        }
        return where(notInSpec(column, values));
    }

    /**
     * {@code column IS NULL}.
     *
     * @param column attribute name
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNull(String column) {
        if (filters != null) {
            return where(filters.hasPropertyIsNull(column));
        }
        return where((root, query, cb) -> cb.isNull(root.get(column)));
    }

    /**
     * {@code column IS NOT NULL}.
     *
     * @param column attribute name
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNotNull(String column) {
        if (filters != null) {
            return where(filters.hasPropertyIsNotNull(column));
        }
        return where((root, query, cb) -> cb.isNotNull(root.get(column)));
    }

    /**
     * Alias for {@link #whereGreaterThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGt(String column, Y value) {
        return whereGreaterThan(column, value);
    }

    /**
     * Strict {@code column > value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGreaterThan(String column, Y value) {
        Objects.requireNonNull(value, "value");
        if (filters != null) {
            return where(filters.hasPropertyGreaterThan(column, value));
        }
        return where((root, query, cb) -> cb.greaterThan(root.get(column), value));
    }

    /**
     * Alias for {@link #whereGreaterThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGte(String column, Y value) {
        return whereGreaterThanOrEqualTo(column, value);
    }

    /**
     * Strict {@code column >= value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGreaterThanOrEqualTo(String column, Y value) {
        Objects.requireNonNull(value, "value");
        if (filters != null) {
            return where(filters.hasPropertyGreaterThanOrEqualTo(column, value));
        }
        return where((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(column), value));
    }

    /**
     * Alias for {@link #whereLessThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLt(String column, Y value) {
        return whereLessThan(column, value);
    }

    /**
     * Strict {@code column < value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLessThan(String column, Y value) {
        Objects.requireNonNull(value, "value");
        if (filters != null) {
            return where(filters.hasPropertyLessThan(column, value));
        }
        return where((root, query, cb) -> cb.lessThan(root.get(column), value));
    }

    /**
     * Alias for {@link #whereLessThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLte(String column, Y value) {
        return whereLessThanOrEqualTo(column, value);
    }

    /**
     * Strict {@code column <= value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLessThanOrEqualTo(String column, Y value) {
        Objects.requireNonNull(value, "value");
        if (filters != null) {
            return where(filters.hasPropertyLessThanOrEqualTo(column, value));
        }
        return where((root, query, cb) -> cb.lessThanOrEqualTo(root.get(column), value));
    }

    /**
     * Inclusive range. If only one bound is non-null, only that bound is applied.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param from   inclusive lower bound; may be {@code null} if {@code to} is set
     * @param to     inclusive upper bound; may be {@code null} if {@code from} is set
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if both {@code from} and {@code to} are {@code null}
     * @see #optionalWhereBetween(String, Comparable, Comparable)
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereBetween(String column, Y from, Y to) {
        if (from == null && to == null) {
            throw new IllegalArgumentException("whereBetween: from and to cannot both be null");
        }
        return where(betweenSpec(column, from, to));
    }

    /**
     * Negated inclusive range: {@code NOT (column BETWEEN from AND to)}.
     * Both bounds are required.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param from   inclusive lower bound; must not be {@code null}
     * @param to     inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code from} or {@code to} is {@code null}
     * @see #optionalWhereNotBetween(String, Comparable, Comparable)
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereNotBetween(String column, Y from, Y to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return where(notBetweenSpec(column, from, to));
    }

    // -------------------------------------------------------------------------
    // date / time extract (Hibernate-portable cb.function names)
    // -------------------------------------------------------------------------

    /**
     * Matches calendar date of a temporal column ({@code year} AND {@code month} AND {@code day}).
     * Uses Criteria {@code cb.function} with lowercase names Hibernate maps portably.
     *
     * @param column temporal attribute name
     * @param date   calendar date; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code date} is {@code null}
     */
    public FluentQuery<T> whereDate(String column, LocalDate date) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(date, "date");
        return where(dateEqualsSpec(column, date));
    }

    /**
     * Matches the year part of a temporal column.
     *
     * @param column temporal attribute name
     * @param year   calendar year
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereYear(String column, int year) {
        Objects.requireNonNull(column, "column");
        return where(datePartEqualsSpec(column, "year", year));
    }

    /**
     * Matches the month part of a temporal column ({@code 1}–{@code 12}).
     *
     * @param column temporal attribute name
     * @param month  month of year ({@code 1}–{@code 12})
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code month} is outside {@code 1}–{@code 12}
     */
    public FluentQuery<T> whereMonth(String column, int month) {
        Objects.requireNonNull(column, "column");
        validateMonth(month);
        return where(datePartEqualsSpec(column, "month", month));
    }

    /**
     * Matches the day-of-month part of a temporal column ({@code 1}–{@code 31}).
     *
     * @param column temporal attribute name
     * @param day    day of month ({@code 1}–{@code 31})
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code day} is outside {@code 1}–{@code 31}
     */
    public FluentQuery<T> whereDay(String column, int day) {
        Objects.requireNonNull(column, "column");
        validateDay(day);
        return where(datePartEqualsSpec(column, "day", day));
    }

    /**
     * Matches clock time of a temporal column ({@code hour} AND {@code minute} AND {@code second}).
     *
     * @param column temporal attribute name
     * @param time   clock time; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code time} is {@code null}
     */
    public FluentQuery<T> whereTime(String column, LocalTime time) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(time, "time");
        return where(timeEqualsSpec(column, time));
    }

    // -------------------------------------------------------------------------
    // column-to-column comparison
    // -------------------------------------------------------------------------

    /**
     * Compares two root attributes for equality ({@code left = right}).
     *
     * @param left  left attribute name
     * @param right right attribute name
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereColumn(String left, String right) {
        return whereColumn(left, "=", right);
    }

    /**
     * Compares two root attributes with a relational operator.
     * Supported: {@code =}, {@code !=}, {@code <>}, {@code >}, {@code >=}, {@code <}, {@code <=}
     * ({@code <>} is normalized to {@code !=}).
     *
     * @param left     left attribute name
     * @param operator comparison operator
     * @param right    right attribute name
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code operator} is not supported
     */
    public FluentQuery<T> whereColumn(String left, String operator, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        String op = normalizeColumnOperator(operator);
        return where(columnCompareSpec(left, op, right));
    }

    /**
     * OR of column equality ({@code left = right}).
     *
     * @param left  left attribute name
     * @param right right attribute name
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereColumn(String left, String right) {
        return orWhereColumn(left, "=", right);
    }

    /**
     * OR of a column-to-column comparison. Same operators as {@link #whereColumn(String, String, String)}.
     *
     * @param left     left attribute name
     * @param operator comparison operator
     * @param right    right attribute name
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code operator} is not supported
     */
    public FluentQuery<T> orWhereColumn(String left, String operator, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        String op = normalizeColumnOperator(operator);
        return orWhere(columnCompareSpec(left, op, right));
    }

    // -------------------------------------------------------------------------
    // optionalWhere* — search filters: if no value, predicate is not applied
    // -------------------------------------------------------------------------

    /**
     * Equality only when a value is present ({@code null} / blank → no-op).
     * Ideal for search endpoints with optional query params.
     *
     * @param column attribute name
     * @param value  expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     * @see #where(String, Object)
     */
    public FluentQuery<T> optionalWhere(String column, Object value) {
        return optionalWhereEqual(column, value);
    }

    /**
     * Same as {@link #optionalWhere(String, Object)}.
     *
     * @param column attribute name
     * @param value  expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereEqual(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : where(strictEqualSpec(column, normalized));
    }

    /**
     * Case-insensitive equality only when {@code value} has text after trim
     * ({@code null} / blank → no-op). Uses {@link java.util.Locale#ROOT} on the
     * Java side; DB {@code UPPER} is vendor-defined.
     *
     * @param column attribute name
     * @param value  expected value; blank → no-op
     * @return {@code this} for chaining
     * @see #whereEqualIgnoreCase(String, String)
     */
    public FluentQuery<T> optionalWhereEqualIgnoreCase(String column, String value) {
        String normalized = Values.trimToNullUpper(value);
        return normalized == null ? this : where(equalIgnoreCaseSpec(column, normalized));
    }

    /**
     * OR equality only when a value is present ({@code null} / blank → no-op).
     *
     * @param column attribute name
     * @param value  expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     * @see #orWhere(String, Object)
     */
    public FluentQuery<T> optionalOrWhere(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : orWhere(strictEqualSpec(column, normalized));
    }

    /**
     * OR {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  substring; blank → no-op
     * @return {@code this} for chaining
     * @see #optionalWhereLike(String, String)
     */
    public FluentQuery<T> optionalOrWhereLike(String column, String value) {
        return Values.isBlank(value) ? this : orWhere(likeSpec(column, value));
    }

    /**
     * OR {@code IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name
     * @param values allowed values; empty → no-op
     * @return {@code this} for chaining
     * @see #optionalWhereIn(String, Collection)
     */
    public FluentQuery<T> optionalOrWhereIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : orWhere(inSpec(column, values));
    }

    /**
     * OR inequality only when a value is present ({@code null} / blank → no-op).
     *
     * @param column attribute name
     * @param value  forbidden value; blank strings are treated as absent
     * @return {@code this} for chaining
     * @see #optionalWhereNotEqual(String, Object)
     */
    public FluentQuery<T> optionalOrWhereNotEqual(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : orWhere(strictNotEqualSpec(column, normalized));
    }

    /**
     * OR {@code NOT IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name
     * @param values forbidden values; empty → no-op
     * @return {@code this} for chaining
     * @see #optionalWhereNotIn(String, Collection)
     */
    public FluentQuery<T> optionalOrWhereNotIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : orWhere(notInSpec(column, values));
    }

    /**
     * Inequality only when a value is present ({@code null} / blank → no-op).
     *
     * @param column attribute name
     * @param value  forbidden value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereNotEqual(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : where(strictNotEqualSpec(column, normalized));
    }

    /**
     * Related equality only when a value is present ({@code null} / blank → no-op).
     *
     * @param relation association name from the root
     * @param column   attribute on the related entity
     * @param value    expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     * @see #whereRelatedEqual(String, String, Object)
     */
    public FluentQuery<T> optionalWhereRelatedEqual(String relation, String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        if (normalized == null) {
            return this;
        }
        return where((root, query, cb) -> {
            Join<?, ?> join = Joins.joinPath(root, relation, JoinType.INNER);
            return cb.equal(join.get(column), normalized);
        });
    }

    /**
     * {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  substring; blank → no-op
     * @return {@code this} for chaining
     * @see #whereLike(String, String)
     */
    public FluentQuery<T> optionalWhereLike(String column, String value) {
        return Values.isBlank(value) ? this : where(likeSpec(column, value));
    }

    /**
     * Escaped contains {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  free-text substring; blank → no-op
     * @return {@code this} for chaining
     * @see #whereContains(String, String)
     */
    public FluentQuery<T> optionalWhereContains(String column, String value) {
        return Values.isBlank(value) ? this : whereContains(column, value);
    }

    /**
     * Escaped prefix {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  prefix; blank → no-op
     * @return {@code this} for chaining
     * @see #whereStartsWith(String, String)
     */
    public FluentQuery<T> optionalWhereStartsWith(String column, String value) {
        return Values.isBlank(value) ? this : whereStartsWith(column, value);
    }

    /**
     * Escaped suffix {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  suffix; blank → no-op
     * @return {@code this} for chaining
     * @see #whereEndsWith(String, String)
     */
    public FluentQuery<T> optionalWhereEndsWith(String column, String value) {
        return Values.isBlank(value) ? this : whereEndsWith(column, value);
    }

    /**
     * Raw {@code LIKE} pattern only when {@code pattern} has text (blank → no-op).
     *
     * @param column  attribute name
     * @param pattern raw LIKE pattern; blank → no-op
     * @return {@code this} for chaining
     * @see #whereLikePattern(String, String)
     */
    public FluentQuery<T> optionalWhereLikePattern(String column, String pattern) {
        return Values.isBlank(pattern) ? this : whereLikePattern(column, pattern);
    }

    /**
     * Related {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param relation association name from the root
     * @param column   attribute on the related entity
     * @param value    substring; blank → no-op
     * @return {@code this} for chaining
     * @see #whereRelatedLike(String, String, String)
     */
    public FluentQuery<T> optionalWhereRelatedLike(String relation, String column, String value) {
        return Values.isBlank(value) ? this : where(relatedLikeSpec(relation, column, value));
    }

    /**
     * {@code IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name
     * @param values allowed values; empty → no-op
     * @return {@code this} for chaining
     * @see #whereIn(String, Collection)
     */
    public FluentQuery<T> optionalWhereIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : where(inSpec(column, values));
    }

    /**
     * {@code NOT IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name
     * @param values forbidden values; empty → no-op
     * @return {@code this} for chaining
     * @see #whereNotIn(String, Collection)
     */
    public FluentQuery<T> optionalWhereNotIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : where(notInSpec(column, values));
    }

    /**
     * Alias for {@link #optionalWhereGreaterThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGt(String column, Y value) {
        return optionalWhereGreaterThan(column, value);
    }

    /**
     * {@code >} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGreaterThan(String column, Y value) {
        return value == null ? this : whereGreaterThan(column, value);
    }

    /**
     * Alias for {@link #optionalWhereGreaterThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGte(String column, Y value) {
        return optionalWhereGreaterThanOrEqualTo(column, value);
    }

    /**
     * {@code >=} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGreaterThanOrEqualTo(
            String column, Y value) {
        return value == null ? this : whereGreaterThanOrEqualTo(column, value);
    }

    /**
     * Alias for {@link #optionalWhereLessThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLt(String column, Y value) {
        return optionalWhereLessThan(column, value);
    }

    /**
     * {@code <} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLessThan(String column, Y value) {
        return value == null ? this : whereLessThan(column, value);
    }

    /**
     * Alias for {@link #optionalWhereLessThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLte(String column, Y value) {
        return optionalWhereLessThanOrEqualTo(column, value);
    }

    /**
     * {@code <=} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param value  inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLessThanOrEqualTo(
            String column, Y value) {
        return value == null ? this : whereLessThanOrEqualTo(column, value);
    }

    /**
     * Inclusive range only when at least one bound is present; both {@code null} → no-op.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param from   inclusive lower bound; may be {@code null}
     * @param to     inclusive upper bound; may be {@code null}
     * @return {@code this} for chaining
     * @see #whereBetween(String, Comparable, Comparable)
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereBetween(String column, Y from, Y to) {
        if (from == null && to == null) {
            return this;
        }
        return where(betweenSpec(column, from, to));
    }

    /**
     * {@code NOT BETWEEN} only when both bounds are non-null; either {@code null} → no-op.
     *
     * @param <Y>    comparable type
     * @param column attribute name
     * @param from   inclusive lower bound; {@code null} → no-op
     * @param to     inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     * @see #whereNotBetween(String, Comparable, Comparable)
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereNotBetween(String column, Y from, Y to) {
        if (from == null || to == null) {
            return this;
        }
        return whereNotBetween(column, from, to);
    }

    /**
     * Date match only when {@code date} is non-null.
     *
     * @param column temporal attribute name
     * @param date   calendar date; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereDate(String column, LocalDate date) {
        return date == null ? this : whereDate(column, date);
    }

    /**
     * Year match only when {@code year} is non-null.
     *
     * @param column temporal attribute name
     * @param year   calendar year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereYear(String column, Integer year) {
        return year == null ? this : whereYear(column, year);
    }

    /**
     * Month match only when {@code month} is non-null ({@code 1}–{@code 12} when present).
     *
     * @param column temporal attribute name
     * @param month  month of year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereMonth(String column, Integer month) {
        return month == null ? this : whereMonth(column, month);
    }

    /**
     * Day match only when {@code day} is non-null ({@code 1}–{@code 31} when present).
     *
     * @param column temporal attribute name
     * @param day    day of month; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereDay(String column, Integer day) {
        return day == null ? this : whereDay(column, day);
    }

    /**
     * Time match only when {@code time} is non-null.
     *
     * @param column temporal attribute name
     * @param time   clock time; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereTime(String column, LocalTime time) {
        return time == null ? this : whereTime(column, time);
    }

    // -------------------------------------------------------------------------
    // associations (Eloquent whereHas / whereDoesntHave)
    // -------------------------------------------------------------------------

    /**
     * Related association exists (collection → {@code IS NOT EMPTY}; to-one → {@code IS NOT NULL}).
     * Supports dotted paths ({@code "company.address"}) via {@code EXISTS} + joins.
     *
     * @param relation association name or dotted path on the root entity
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code relation} is {@code null}
     */
    public FluentQuery<T> whereHas(String relation) {
        Objects.requireNonNull(relation, "relation");
        if (Joins.isNestedPath(relation)) {
            return where(existsPathSpec(relation, false));
        }
        if (filters != null) {
            return where(filters.hasRelation(relation));
        }
        return where(hasRelationSpec(relation));
    }

    /**
     * Related rows exist matching nested predicates ({@code EXISTS} correlated subquery).
     * Supports dotted paths; predicates apply to the <em>leaf</em> association.
     *
     * <pre>{@code
     * // single hop
     * authorRepository.query()
     *     .whereHas("books", f -> f.whereGt("pages", 100))
     *     .get();
     *
     * // nested path (equivalent to whereHas("company", f -> f.whereHas(...)))
     * authorRepository.query()
     *     .whereHas("company.address", f -> f.where("city", "Austin"))
     *     .get();
     * }</pre>
     *
     * @param relation association name or dotted path on the root entity
     * @param nested   consumer that configures a {@link RelatedFilter} on the leaf join
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code relation} or {@code nested} is {@code null}
     * @see RelatedFilter
     */
    public FluentQuery<T> whereHas(String relation, Consumer<RelatedFilter> nested) {
        return whereExists(relation, nested, false);
    }

    /**
     * Related association absent (collection → {@code IS EMPTY}; to-one → {@code IS NULL}).
     * Supports dotted paths via {@code NOT EXISTS} + joins.
     *
     * @param relation association name or dotted path on the root entity
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code relation} is {@code null}
     */
    public FluentQuery<T> whereDoesntHave(String relation) {
        Objects.requireNonNull(relation, "relation");
        if (Joins.isNestedPath(relation)) {
            return where(existsPathSpec(relation, true));
        }
        if (filters != null) {
            return where(filters.hasNoRelation(relation));
        }
        return where(doesntHaveRelationSpec(relation));
    }

    /**
     * No related rows match the nested predicates ({@code NOT EXISTS} correlated subquery).
     *
     * @param relation association name on the root entity
     * @param nested   consumer that configures a {@link RelatedFilter} on the join
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code relation} or {@code nested} is {@code null}
     * @see RelatedFilter
     */
    public FluentQuery<T> whereDoesntHave(String relation, Consumer<RelatedFilter> nested) {
        return whereExists(relation, nested, true);
    }

    /**
     * OR of {@link #whereHas(String)}.
     *
     * @param relation association name or dotted path on the root entity
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(String relation) {
        Objects.requireNonNull(relation, "relation");
        if (Joins.isNestedPath(relation)) {
            return orWhere(existsPathSpec(relation, false));
        }
        if (filters != null) {
            return orWhere(filters.hasRelation(relation));
        }
        return orWhere(hasRelationSpec(relation));
    }

    /**
     * OR of nested {@code EXISTS} ({@link #whereHas(String, Consumer)}).
     *
     * @param relation association name on the root entity
     * @param nested   consumer that configures a {@link RelatedFilter} on the join
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(String relation, Consumer<RelatedFilter> nested) {
        return orWhereExists(relation, nested, false);
    }

    /**
     * OR of {@link #whereDoesntHave(String)}.
     *
     * @param relation association name or dotted path on the root entity
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(String relation) {
        Objects.requireNonNull(relation, "relation");
        if (Joins.isNestedPath(relation)) {
            return orWhere(existsPathSpec(relation, true));
        }
        if (filters != null) {
            return orWhere(filters.hasNoRelation(relation));
        }
        return orWhere(doesntHaveRelationSpec(relation));
    }

    /**
     * OR of nested {@code NOT EXISTS} ({@link #whereDoesntHave(String, Consumer)}).
     *
     * @param relation association name on the root entity
     * @param nested   consumer that configures a {@link RelatedFilter} on the join
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(String relation, Consumer<RelatedFilter> nested) {
        return orWhereExists(relation, nested, true);
    }

    /**
     * Convenience for {@code whereHas(relation, f -> f.where(column, value))}.
     * {@code relation} may be a dotted path; {@code column} is on the leaf entity.
     *
     * @param relation association name or dotted path on the root entity
     * @param column   attribute on the related (leaf) entity
     * @param value    expected related value; {@code null} → {@code IS NULL} on the related column
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereRelation(String relation, String column, Object value) {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(column, "column");
        return whereHas(relation, f -> f.where(column, value));
    }

    /**
     * Same as {@link #whereRelation(String, String, Object)} only when {@code value} is present
     * ({@code null} / blank → no-op).
     *
     * @param relation association name on the root entity
     * @param column   attribute on the related entity
     * @param value    expected related value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereRelation(String relation, String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : whereRelation(relation, column, normalized);
    }

    // -------------------------------------------------------------------------
    // type-safe metamodel overloads (SingularAttribute / PluralAttribute)
    // -------------------------------------------------------------------------

    /**
     * Strict equality using a JPA static metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; {@code null} → {@code IS NULL}
     * @return {@code this} for chaining
     * @see #where(String, Object)
     */
    public FluentQuery<T> where(SingularAttribute<? super T, ?> attribute, Object value) {
        return where(attrName(attribute), value);
    }

    /**
     * Strict equality using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; {@code null} → {@code IS NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereEqual(SingularAttribute<? super T, ?> attribute, Object value) {
        return whereEqual(attrName(attribute), value);
    }

    /**
     * Strict inequality using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     forbidden value; {@code null} → {@code IS NOT NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNotEqual(SingularAttribute<? super T, ?> attribute, Object value) {
        return whereNotEqual(attrName(attribute), value);
    }

    /**
     * Case-insensitive {@code LIKE} using a metamodel attribute.
     * Same pattern rules as {@link #whereLike(String, String)}.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     substring or LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereLike(SingularAttribute<? super T, ?> attribute, String value) {
        return whereLike(attrName(attribute), value);
    }

    /**
     * Escaped contains {@code LIKE} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     free-text substring; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereContains(SingularAttribute<? super T, ?> attribute, String value) {
        return whereContains(attrName(attribute), value);
    }

    /**
     * Escaped prefix {@code LIKE} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     prefix; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereStartsWith(SingularAttribute<? super T, ?> attribute, String value) {
        return whereStartsWith(attrName(attribute), value);
    }

    /**
     * Escaped suffix {@code LIKE} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     suffix; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereEndsWith(SingularAttribute<? super T, ?> attribute, String value) {
        return whereEndsWith(attrName(attribute), value);
    }

    /**
     * Raw {@code LIKE} pattern using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param pattern   raw LIKE pattern; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereLikePattern(SingularAttribute<? super T, ?> attribute, String pattern) {
        return whereLikePattern(attrName(attribute), pattern);
    }

    /**
     * Strict {@code IN} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    allowed values; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereIn(SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return whereIn(attrName(attribute), values);
    }

    /**
     * Strict {@code NOT IN} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    forbidden values; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNotIn(SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return whereNotIn(attrName(attribute), values);
    }

    /**
     * {@code IS NULL} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNull(SingularAttribute<? super T, ?> attribute) {
        return whereNull(attrName(attribute));
    }

    /**
     * {@code IS NOT NULL} using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereNotNull(SingularAttribute<? super T, ?> attribute) {
        return whereNotNull(attrName(attribute));
    }

    /**
     * {@code >} using a metamodel attribute.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive lower bound
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGt(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return whereGt(attrName(attribute), value);
    }

    /**
     * {@code >=} using a metamodel attribute.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive lower bound
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereGte(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return whereGte(attrName(attribute), value);
    }

    /**
     * {@code <} using a metamodel attribute.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive upper bound
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLt(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return whereLt(attrName(attribute), value);
    }

    /**
     * {@code <=} using a metamodel attribute.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive upper bound
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereLte(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return whereLte(attrName(attribute), value);
    }

    /**
     * Inclusive range using a metamodel attribute.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param from      inclusive lower bound; may be {@code null} if {@code to} is set
     * @param to        inclusive upper bound; may be {@code null} if {@code from} is set
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereBetween(
            SingularAttribute<? super T, Y> attribute, Y from, Y to) {
        return whereBetween(attrName(attribute), from, to);
    }

    /**
     * Negated inclusive range using a metamodel attribute. Both bounds required.
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param from      inclusive lower bound; must not be {@code null}
     * @param to        inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> whereNotBetween(
            SingularAttribute<? super T, Y> attribute, Y from, Y to) {
        return whereNotBetween(attrName(attribute), from, to);
    }

    /**
     * Calendar date match using a metamodel attribute.
     *
     * @param attribute temporal metamodel attribute
     * @param date      calendar date; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDate(SingularAttribute<? super T, ?> attribute, LocalDate date) {
        return whereDate(attrName(attribute), date);
    }

    /**
     * Year match using a metamodel attribute.
     *
     * @param attribute temporal metamodel attribute
     * @param year      calendar year
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereYear(SingularAttribute<? super T, ?> attribute, int year) {
        return whereYear(attrName(attribute), year);
    }

    /**
     * Month match using a metamodel attribute ({@code 1}–{@code 12}).
     *
     * @param attribute temporal metamodel attribute
     * @param month     month of year
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereMonth(SingularAttribute<? super T, ?> attribute, int month) {
        return whereMonth(attrName(attribute), month);
    }

    /**
     * Day-of-month match using a metamodel attribute ({@code 1}–{@code 31}).
     *
     * @param attribute temporal metamodel attribute
     * @param day       day of month
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDay(SingularAttribute<? super T, ?> attribute, int day) {
        return whereDay(attrName(attribute), day);
    }

    /**
     * Clock time match using a metamodel attribute.
     *
     * @param attribute temporal metamodel attribute
     * @param time      clock time; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereTime(SingularAttribute<? super T, ?> attribute, LocalTime time) {
        return whereTime(attrName(attribute), time);
    }

    /**
     * Column equality using metamodel attributes.
     *
     * @param left  left attribute
     * @param right right attribute
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereColumn(
            SingularAttribute<? super T, ?> left, SingularAttribute<? super T, ?> right) {
        return whereColumn(attrName(left), attrName(right));
    }

    /**
     * Column comparison using metamodel attributes.
     *
     * @param left     left attribute
     * @param operator comparison operator
     * @param right    right attribute
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereColumn(
            SingularAttribute<? super T, ?> left,
            String operator,
            SingularAttribute<? super T, ?> right) {
        return whereColumn(attrName(left), operator, attrName(right));
    }

    /**
     * OR of column equality using metamodel attributes.
     *
     * @param left  left attribute
     * @param right right attribute
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereColumn(
            SingularAttribute<? super T, ?> left, SingularAttribute<? super T, ?> right) {
        return orWhereColumn(attrName(left), attrName(right));
    }

    /**
     * OR of a column comparison using metamodel attributes.
     *
     * @param left     left attribute
     * @param operator comparison operator
     * @param right    right attribute
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereColumn(
            SingularAttribute<? super T, ?> left,
            String operator,
            SingularAttribute<? super T, ?> right) {
        return orWhereColumn(attrName(left), operator, attrName(right));
    }

    /**
     * Optional equality using a metamodel attribute ({@code null}/blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhere(SingularAttribute<? super T, ?> attribute, Object value) {
        return optionalWhere(attrName(attribute), value);
    }

    /**
     * Same as {@link #optionalWhere(SingularAttribute, Object)}.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereEqual(SingularAttribute<? super T, ?> attribute, Object value) {
        return optionalWhereEqual(attrName(attribute), value);
    }

    /**
     * Type-safe {@link #optionalWhereEqualIgnoreCase(String, String)}.
     *
     * @param attribute metamodel attribute
     * @param value     expected value; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereEqualIgnoreCase(
            SingularAttribute<? super T, ?> attribute, String value) {
        return optionalWhereEqualIgnoreCase(attrName(attribute), value);
    }

    /**
     * Type-safe {@link #whereEqualIgnoreCase(String, String)}.
     *
     * @param attribute metamodel attribute
     * @param value     expected value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereEqualIgnoreCase(SingularAttribute<? super T, ?> attribute, String value) {
        return whereEqualIgnoreCase(attrName(attribute), value);
    }

    /**
     * Optional {@code LIKE} using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     substring; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereLike(SingularAttribute<? super T, ?> attribute, String value) {
        return optionalWhereLike(attrName(attribute), value);
    }

    /**
     * Optional escaped contains {@code LIKE} using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     free-text substring; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereContains(SingularAttribute<? super T, ?> attribute, String value) {
        return optionalWhereContains(attrName(attribute), value);
    }

    /**
     * Optional escaped prefix {@code LIKE} using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     prefix; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereStartsWith(SingularAttribute<? super T, ?> attribute, String value) {
        return optionalWhereStartsWith(attrName(attribute), value);
    }

    /**
     * Optional escaped suffix {@code LIKE} using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     suffix; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereEndsWith(SingularAttribute<? super T, ?> attribute, String value) {
        return optionalWhereEndsWith(attrName(attribute), value);
    }

    /**
     * Optional raw {@code LIKE} pattern using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param pattern   raw LIKE pattern; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereLikePattern(SingularAttribute<? super T, ?> attribute, String pattern) {
        return optionalWhereLikePattern(attrName(attribute), pattern);
    }

    /**
     * Optional related equality using metamodel attributes ({@code null}/blank → no-op).
     *
     * @param relation association attribute on the root
     * @param column   attribute on the related entity
     * @param value    expected value; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereRelatedEqual(
            Attribute<? super T, ?> relation,
            SingularAttribute<?, ?> column,
            Object value) {
        return optionalWhereRelatedEqual(attrName(relation), attrName(column), value);
    }

    /**
     * Optional related {@code LIKE} using metamodel attributes (blank → no-op).
     *
     * @param relation association attribute on the root
     * @param column   attribute on the related entity
     * @param value    substring or pattern; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereRelatedLike(
            Attribute<? super T, ?> relation,
            SingularAttribute<?, ?> column,
            String value) {
        return optionalWhereRelatedLike(attrName(relation), attrName(column), value);
    }

    /**
     * Optional {@code IN} using a metamodel attribute (empty → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    allowed values; empty → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return optionalWhereIn(attrName(attribute), values);
    }

    /**
     * Optional {@code NOT IN} using a metamodel attribute (empty → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    forbidden values; empty → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereNotIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return optionalWhereNotIn(attrName(attribute), values);
    }

    /**
     * Optional inequality using a metamodel attribute ({@code null}/blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     forbidden value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereNotEqual(
            SingularAttribute<? super T, ?> attribute, Object value) {
        return optionalWhereNotEqual(attrName(attribute), value);
    }

    /**
     * Optional {@code >} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGt(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereGt(attrName(attribute), value);
    }

    /**
     * Optional {@code >} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGreaterThan(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereGreaterThan(attrName(attribute), value);
    }

    /**
     * Optional {@code >=} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGte(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereGte(attrName(attribute), value);
    }

    /**
     * Optional {@code >=} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereGreaterThanOrEqualTo(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereGreaterThanOrEqualTo(attrName(attribute), value);
    }

    /**
     * Optional {@code <} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLt(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereLt(attrName(attribute), value);
    }

    /**
     * Optional {@code <} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLessThan(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereLessThan(attrName(attribute), value);
    }

    /**
     * Optional {@code <=} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLte(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereLte(attrName(attribute), value);
    }

    /**
     * Optional {@code <=} using a metamodel attribute ({@code null} → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param value     inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereLessThanOrEqualTo(
            SingularAttribute<? super T, Y> attribute, Y value) {
        return optionalWhereLessThanOrEqualTo(attrName(attribute), value);
    }

    /**
     * Optional range using a metamodel attribute (both null → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param from      inclusive lower bound; may be {@code null}
     * @param to        inclusive upper bound; may be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereBetween(
            SingularAttribute<? super T, Y> attribute, Y from, Y to) {
        return optionalWhereBetween(attrName(attribute), from, to);
    }

    /**
     * Optional {@code NOT BETWEEN} using a metamodel attribute (either null → no-op).
     *
     * @param <Y>       comparable type
     * @param attribute metamodel attribute on the root entity
     * @param from      inclusive lower bound; {@code null} → no-op
     * @param to        inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> FluentQuery<T> optionalWhereNotBetween(
            SingularAttribute<? super T, Y> attribute, Y from, Y to) {
        return optionalWhereNotBetween(attrName(attribute), from, to);
    }

    /**
     * Optional date match using a metamodel attribute ({@code null} → no-op).
     *
     * @param attribute temporal metamodel attribute
     * @param date      calendar date; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereDate(SingularAttribute<? super T, ?> attribute, LocalDate date) {
        return optionalWhereDate(attrName(attribute), date);
    }

    /**
     * Optional year match using a metamodel attribute ({@code null} → no-op).
     *
     * @param attribute temporal metamodel attribute
     * @param year      calendar year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereYear(SingularAttribute<? super T, ?> attribute, Integer year) {
        return optionalWhereYear(attrName(attribute), year);
    }

    /**
     * Optional month match using a metamodel attribute ({@code null} → no-op).
     *
     * @param attribute temporal metamodel attribute
     * @param month     month of year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereMonth(SingularAttribute<? super T, ?> attribute, Integer month) {
        return optionalWhereMonth(attrName(attribute), month);
    }

    /**
     * Optional day match using a metamodel attribute ({@code null} → no-op).
     *
     * @param attribute temporal metamodel attribute
     * @param day       day of month; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereDay(SingularAttribute<? super T, ?> attribute, Integer day) {
        return optionalWhereDay(attrName(attribute), day);
    }

    /**
     * Optional time match using a metamodel attribute ({@code null} → no-op).
     *
     * @param attribute temporal metamodel attribute
     * @param time      clock time; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereTime(SingularAttribute<? super T, ?> attribute, LocalTime time) {
        return optionalWhereTime(attrName(attribute), time);
    }

    /**
     * Strict OR equality using a metamodel attribute.
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; {@code null} → {@code IS NULL}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhere(SingularAttribute<? super T, ?> attribute, Object value) {
        return orWhere(attrName(attribute), value);
    }

    /**
     * Optional OR equality using a metamodel attribute ({@code null}/blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalOrWhere(SingularAttribute<? super T, ?> attribute, Object value) {
        return optionalOrWhere(attrName(attribute), value);
    }

    /**
     * Optional OR {@code LIKE} using a metamodel attribute (blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     substring; blank → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalOrWhereLike(SingularAttribute<? super T, ?> attribute, String value) {
        return optionalOrWhereLike(attrName(attribute), value);
    }

    /**
     * Type-safe {@link #orWhereLike(String, String)}.
     */
    public FluentQuery<T> orWhereLike(SingularAttribute<? super T, ?> attribute, String value) {
        return orWhereLike(attrName(attribute), value);
    }

    /**
     * Type-safe {@link #orWhereContains(String, String)}.
     */
    public FluentQuery<T> orWhereContains(SingularAttribute<? super T, ?> attribute, String value) {
        return orWhereContains(attrName(attribute), value);
    }

    /**
     * Type-safe {@link #orWhereIn(String, Collection)}.
     */
    public FluentQuery<T> orWhereIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return orWhereIn(attrName(attribute), values);
    }

    /**
     * Type-safe {@link #orWhereNotIn(String, Collection)}.
     */
    public FluentQuery<T> orWhereNotIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return orWhereNotIn(attrName(attribute), values);
    }


    /**
     * Optional OR {@code IN} using a metamodel attribute (empty → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    allowed values; empty → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalOrWhereIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return optionalOrWhereIn(attrName(attribute), values);
    }

    /**
     * Optional OR inequality using a metamodel attribute ({@code null}/blank → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param value     forbidden value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalOrWhereNotEqual(
            SingularAttribute<? super T, ?> attribute, Object value) {
        return optionalOrWhereNotEqual(attrName(attribute), value);
    }

    /**
     * Optional OR {@code NOT IN} using a metamodel attribute (empty → no-op).
     *
     * @param attribute metamodel attribute on the root entity
     * @param values    forbidden values; empty → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalOrWhereNotIn(
            SingularAttribute<? super T, ?> attribute, Collection<?> values) {
        return optionalOrWhereNotIn(attrName(attribute), values);
    }

    /**
     * Related equality via INNER JOIN using metamodel attributes.
     *
     * @param <R>      related entity type
     * @param relation to-one association from the root
     * @param column   attribute on the related entity
     * @param value    expected value; {@code null} → {@code IS NULL} on the join path
     * @return {@code this} for chaining
     */
    public <R> FluentQuery<T> whereRelatedEqual(
            SingularAttribute<? super T, R> relation,
            SingularAttribute<? super R, ?> column,
            Object value) {
        return whereRelatedEqual(attrName(relation), attrName(column), value);
    }

    /**
     * Related {@code LIKE} via INNER JOIN using metamodel attributes.
     *
     * @param <R>      related entity type
     * @param relation to-one association from the root
     * @param column   attribute on the related entity
     * @param value    substring to match; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <R> FluentQuery<T> whereRelatedLike(
            SingularAttribute<? super T, R> relation,
            SingularAttribute<? super R, ?> column,
            String value) {
        return whereRelatedLike(attrName(relation), attrName(column), value);
    }

    /**
     * Ascending order by metamodel attributes.
     *
     * @param attributes sort attributes; empty → no-op
     * @return {@code this} for chaining
     */
    @SafeVarargs
    public final FluentQuery<T> orderByAsc(SingularAttribute<? super T, ?>... attributes) {
        return orderByAsc(attrNames(attributes));
    }

    /**
     * Descending order by metamodel attributes.
     *
     * @param attributes sort attributes; empty → no-op
     * @return {@code this} for chaining
     */
    @SafeVarargs
    public final FluentQuery<T> orderByDesc(SingularAttribute<? super T, ?>... attributes) {
        return orderByDesc(attrNames(attributes));
    }

    /**
     * {@code orderByDesc(attribute)} + {@link #first()}.
     *
     * @param attribute sort attribute (descending)
     * @return latest matching entity, or empty
     */
    public Optional<T> latest(SingularAttribute<? super T, ?> attribute) {
        return latest(attrName(attribute));
    }

    /**
     * {@code orderByAsc(attribute)} + {@link #first()}.
     *
     * @param attribute sort attribute (ascending)
     * @return oldest matching entity, or empty
     */
    public Optional<T> oldest(SingularAttribute<? super T, ?> attribute) {
        return oldest(attrName(attribute));
    }

    /**
     * LEFT JOIN FETCH of to-one associations using metamodel attributes.
     *
     * @param associations to-one associations
     * @return {@code this} for chaining
     * @see #fetch(String...)
     */
    @SafeVarargs
    public final FluentQuery<T> fetch(SingularAttribute<? super T, ?>... associations) {
        return fetch(attrNames(associations));
    }

    /**
     * LEFT JOIN FETCH of collections using metamodel attributes.
     *
     * @param associations to-many associations
     * @return {@code this} for chaining
     * @see #fetchCollection(String...)
     */
    @SafeVarargs
    public final FluentQuery<T> fetchCollection(PluralAttribute<? super T, ?, ?>... associations) {
        return fetchCollection(attrNames(associations));
    }

    /**
     * Association exists (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereHas(SingularAttribute<? super T, ?> relation) {
        return whereHas(attrName(relation));
    }

    /**
     * Association exists (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereHas(PluralAttribute<? super T, ?, ?> relation) {
        return whereHas(attrName(relation));
    }

    /**
     * Nested {@code EXISTS} for a to-one association using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereHas(
            SingularAttribute<? super T, ?> relation, Consumer<RelatedFilter> nested) {
        return whereHas(attrName(relation), nested);
    }

    /**
     * Nested {@code EXISTS} for a to-many association using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereHas(
            PluralAttribute<? super T, ?, ?> relation, Consumer<RelatedFilter> nested) {
        return whereHas(attrName(relation), nested);
    }

    /**
     * Association absent (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDoesntHave(SingularAttribute<? super T, ?> relation) {
        return whereDoesntHave(attrName(relation));
    }

    /**
     * Association absent (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDoesntHave(PluralAttribute<? super T, ?, ?> relation) {
        return whereDoesntHave(attrName(relation));
    }

    /**
     * Nested {@code NOT EXISTS} for a to-one association using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDoesntHave(
            SingularAttribute<? super T, ?> relation, Consumer<RelatedFilter> nested) {
        return whereDoesntHave(attrName(relation), nested);
    }

    /**
     * Nested {@code NOT EXISTS} for a to-many association using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereDoesntHave(
            PluralAttribute<? super T, ?, ?> relation, Consumer<RelatedFilter> nested) {
        return whereDoesntHave(attrName(relation), nested);
    }

    /**
     * OR of association exists (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(SingularAttribute<? super T, ?> relation) {
        return orWhereHas(attrName(relation));
    }

    /**
     * OR of association exists (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(PluralAttribute<? super T, ?, ?> relation) {
        return orWhereHas(attrName(relation));
    }

    /**
     * OR of nested {@code EXISTS} (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(
            SingularAttribute<? super T, ?> relation, Consumer<RelatedFilter> nested) {
        return orWhereHas(attrName(relation), nested);
    }

    /**
     * OR of nested {@code EXISTS} (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereHas(
            PluralAttribute<? super T, ?, ?> relation, Consumer<RelatedFilter> nested) {
        return orWhereHas(attrName(relation), nested);
    }

    /**
     * OR of association absent (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(SingularAttribute<? super T, ?> relation) {
        return orWhereDoesntHave(attrName(relation));
    }

    /**
     * OR of association absent (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(PluralAttribute<? super T, ?, ?> relation) {
        return orWhereDoesntHave(attrName(relation));
    }

    /**
     * OR of nested {@code NOT EXISTS} (to-one) using a metamodel attribute.
     *
     * @param relation to-one association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(
            SingularAttribute<? super T, ?> relation, Consumer<RelatedFilter> nested) {
        return orWhereDoesntHave(attrName(relation), nested);
    }

    /**
     * OR of nested {@code NOT EXISTS} (to-many) using a metamodel attribute.
     *
     * @param relation to-many association on the root
     * @param nested   related filter consumer
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orWhereDoesntHave(
            PluralAttribute<? super T, ?, ?> relation, Consumer<RelatedFilter> nested) {
        return orWhereDoesntHave(attrName(relation), nested);
    }

    /**
     * Related column equality (to-one) using a metamodel association.
     *
     * @param relation to-one association on the root
     * @param column   attribute on the related entity
     * @param value    expected related value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereRelation(
            SingularAttribute<? super T, ?> relation, String column, Object value) {
        return whereRelation(attrName(relation), column, value);
    }

    /**
     * Related column equality (to-many) using a metamodel association.
     *
     * @param relation to-many association on the root
     * @param column   attribute on the related entity
     * @param value    expected related value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> whereRelation(
            PluralAttribute<? super T, ?, ?> relation, String column, Object value) {
        return whereRelation(attrName(relation), column, value);
    }

    /**
     * Optional related column equality (to-one); blank/null value → no-op.
     *
     * @param relation to-one association on the root
     * @param column   attribute on the related entity
     * @param value    expected related value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereRelation(
            SingularAttribute<? super T, ?> relation, String column, Object value) {
        return optionalWhereRelation(attrName(relation), column, value);
    }

    /**
     * Optional related column equality (to-many); blank/null value → no-op.
     *
     * @param relation to-many association on the root
     * @param column   attribute on the related entity
     * @param value    expected related value
     * @return {@code this} for chaining
     */
    public FluentQuery<T> optionalWhereRelation(
            PluralAttribute<? super T, ?, ?> relation, String column, Object value) {
        return optionalWhereRelation(attrName(relation), column, value);
    }

    // -------------------------------------------------------------------------
    // select / fetch / distinct / limit / order
    // -------------------------------------------------------------------------

    /**
     * Restrict which properties are loaded (Eloquent-style {@code select}).
     *
     * <p>Delegates to Spring Data {@code SpecificationFluentQuery#project(Collection)}.
     * Repeated calls <em>append</em> (deduplicated, order preserved).
     *
     * <p><b>Honest contract (JPA):</b>
     * <ul>
     *   <li>With interface/DTO terminals ({@link #firstAs}, {@link #getAs}, {@link #pageAs}) —
     *       Spring Data can limit the selected columns to these property paths. Prefer this
     *       when you want a lean SQL projection.</li>
     *   <li>With entity terminals ({@link #get}, {@link #first}, …) — JPA cannot return a
     *       “partial entity” the way Eloquent returns a model with only some attributes;
     *       {@code project} applies Spring Data’s property/EntityGraph rules. For true
     *       column trimming, use {@code select(...).getAs(YourProjection.class)}.</li>
     * </ul>
     *
     * @param columns attribute names, property paths (e.g. {@code "email"}), or compact
     *                association shorthand {@code "assoc:col1,col2"} → {@code assoc.col1},
     *                {@code assoc.col2} (also nested: {@code "a.b:c,d"});
     *                at least one required; blank names rejected
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code columns} or an element is {@code null}
     * @throws IllegalArgumentException if {@code columns} is empty or an element is blank/malformed
     * @see #select(java.util.Collection)
     * @see #firstAs(Class)
     * @see SelectPaths
     */
    public FluentQuery<T> select(String... columns) {
        ensureOpen();
        Objects.requireNonNull(columns, "columns");
        if (columns.length == 0) {
            throw new IllegalArgumentException("select requires at least one column");
        }
        for (String column : columns) {
            Objects.requireNonNull(column, "column");
            Values.requireText(column, "select column must not be blank");
            for (String name : SelectPaths.expand(column)) {
                if (!selectColumns.contains(name)) {
                    selectColumns.add(name);
                }
            }
        }
        return this;
    }

    /**
     * Same as {@link #select(String...)} from a collection (Eloquent {@code select([...])}).
     *
     * @param columns non-empty collection of attribute names
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code columns} is {@code null}
     * @throws IllegalArgumentException if {@code columns} is empty or contains blank names
     */
    public FluentQuery<T> select(Collection<String> columns) {
        Objects.requireNonNull(columns, "columns");
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("select requires at least one column");
        }
        return select(columns.toArray(String[]::new));
    }

    /**
     * Type-safe {@link #select(String...)} using metamodel attributes.
     *
     * @param attributes non-empty metamodel attributes on the root
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code attributes} or an element is {@code null}
     * @throws IllegalArgumentException if {@code attributes} is empty
     */
    @SafeVarargs
    public final FluentQuery<T> select(SingularAttribute<? super T, ?>... attributes) {
        Objects.requireNonNull(attributes, "attributes");
        if (attributes.length == 0) {
            throw new IllegalArgumentException("select requires at least one attribute");
        }
        String[] names = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            names[i] = attrName(attributes[i]);
        }
        return select(names);
    }

    /**
     * LEFT JOIN FETCH of <b>to-one</b> associations ({@code @ManyToOne}/{@code @OneToOne}).
     * Supports dotted paths ({@code "profile.address"}); intermediate fetches are reused.
     * Safe with {@link #page(Pageable)}. Enables DISTINCT. Not applied on count/exists.
     *
     * <p><b>Not</b> a column list: {@code "status:id,name"} is rejected. JPA loads the full
     * managed association; for lean columns use
     * {@code select("status:id,name").getAs(Projection.class)} instead.
     *
     * @param associations to-one association names or dotted paths
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if a token contains {@code ':'} (select shorthand)
     */
    public FluentQuery<T> fetch(String... associations) {
        ensureOpen();
        addFetches(fetchToOne, fetchCollections, associations);
        if (!fetchToOne.isEmpty() || !fetchCollections.isEmpty()) {
            this.distinct = true;
        }
        return this;
    }

    /**
     * Constrained to-one eager load (Eloquent {@code with} + closure): {@code LEFT JOIN FETCH}
     * with related predicates on the leaf join {@code ON} clause.
     *
     * <pre>{@code
     * query().fetch("profile", f -> f.where("active", true)).first();
     * query().fetch("company.address", f -> f.whereNotNull("city"));
     * }</pre>
     *
     * @param association to-one path (may be dotted; constraints apply to the leaf)
     * @param constraints related predicates; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code constraints} is {@code null}
     * @see #with(String, Consumer)
     */
    public FluentQuery<T> fetch(String association, Consumer<RelatedFilter> constraints) {
        Objects.requireNonNull(constraints, "constraints");
        Values.requireText(association, "association must not be blank");
        String path = association.trim();
        fetch(path);
        fetchOnConstraints.put(path, constraints);
        return this;
    }

    /**
     * Batch to-one eager loads mixing plain and constrained entries — Java form of Eloquent
     * {@code with(['a.b' => fn, 'c', 'd' => fn])}.
     *
     * <pre>{@code
     * query().fetch(
     *     FetchRel.of("rel1.rel2", f -> f.where("active", true)),
     *     FetchRel.of("rel3"),
     *     FetchRel.of("rel4", f -> f.whereNotNull("code"))
     * ).first();
     * }</pre>
     *
     * @param relations one or more {@link FetchRel} specs
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code relations} or an element is {@code null}
     * @throws IllegalArgumentException if empty
     * @see FetchRel
     * @see #with(FetchRel...)
     */
    public FluentQuery<T> fetch(FetchRel... relations) {
        ensureOpen();
        Objects.requireNonNull(relations, "relations");
        if (relations.length == 0) {
            throw new IllegalArgumentException("fetch requires at least one FetchRel");
        }
        for (FetchRel rel : relations) {
            Objects.requireNonNull(rel, "relation");
            if (rel.constrained()) {
                fetch(rel.path(), rel.constraints());
            } else {
                fetch(rel.path());
            }
        }
        return this;
    }

    /**
     * Eloquent-style eager load for <b>to-one</b> associations (alias of {@link #fetch(String...)}).
     *
     * <pre>{@code
     * query().with("profile", "company.address").first();
     * }</pre>
     *
     * @param associations to-one paths
     * @return {@code this} for chaining
     * @see #fetch(String, Consumer)
     * @see #withCollection(String, Consumer)
     */
    public FluentQuery<T> with(String... associations) {
        return fetch(associations);
    }

    /**
     * Alias of {@link #fetch(String, Consumer)} (Eloquent naming).
     *
     * @param association to-one path
     * @param constraints related predicates; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> with(String association, Consumer<RelatedFilter> constraints) {
        return fetch(association, constraints);
    }

    /**
     * Alias of {@link #fetch(FetchRel...)}.
     *
     * @param relations fetch specs
     * @return {@code this} for chaining
     */
    public FluentQuery<T> with(FetchRel... relations) {
        return fetch(relations);
    }

    /**
     * Batch Eloquent-style to-one {@code with}. A {@code null} consumer means plain fetch.
     * Prefer {@link #fetch(FetchRel...)} when mixing plain and constrained paths (no nulls).
     *
     * <pre>{@code
     * Map<String, Consumer<RelatedFilter>> rels = new LinkedHashMap<>();
     * rels.put("profile", null);
     * rels.put("company", f -> f.where("active", true));
     * query().with(rels);
     * }</pre>
     *
     * @param relations path → constraints ({@code null} value = unconstrained fetch)
     * @return {@code this} for chaining
     */
    public FluentQuery<T> with(java.util.Map<String, ? extends Consumer<RelatedFilter>> relations) {
        ensureOpen();
        Objects.requireNonNull(relations, "relations");
        for (var e : relations.entrySet()) {
            if (e.getValue() == null) {
                with(e.getKey());
            } else {
                with(e.getKey(), e.getValue());
            }
        }
        return this;
    }

    /**
     * Same as {@link #with(java.util.Map)} under the {@code fetch} name.
     *
     * @param relations path → constraints ({@code null} value = unconstrained fetch)
     * @return {@code this} for chaining
     */
    public FluentQuery<T> fetch(java.util.Map<String, ? extends Consumer<RelatedFilter>> relations) {
        return with(relations);
    }

    /**
     * LEFT JOIN FETCH of collections ({@code @OneToMany}/{@code @ManyToMany}).
     * <b>Incompatible</b> with {@link #page(Pageable)} and {@link #limit(int)}
     * (cartesian product / wrong COUNT or LIMIT on the join product).
     * Use with {@link #get()} / {@link #first()}, or load collections in a second query.
     *
     * <p>{@link #first()} / {@link #one()} with a collection fetch skip SQL {@code LIMIT} and
     * pick the first root in memory. Constrained collection fetch is a <em>partial</em> view —
     * do not {@code save()} roots with {@code orphanRemoval} after filtering children.
     *
     * <p>Same rule as {@link #fetch}: no {@code "assoc:col1,col2"} shorthand — use {@link #select}.
     *
     * @param associations to-many association names
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if a token contains {@code ':'} (select shorthand)
     */
    public FluentQuery<T> fetchCollection(String... associations) {
        ensureOpen();
        addFetches(fetchCollections, fetchToOne, associations);
        assertNoCollectionFetchWithLimit();
        if (!fetchToOne.isEmpty() || !fetchCollections.isEmpty()) {
            this.distinct = true;
        }
        return this;
    }

    /**
     * Constrained collection eager load: {@code LEFT JOIN FETCH} + {@code ON} on the leaf
     * (Eloquent {@code with(['books' => fn ($q) => ...])}). Same page/{@code limit} rules as
     * {@link #fetchCollection(String...)}.
     *
     * <pre>{@code
     * query().fetchCollection("books", f -> f.whereGt("pages", 100)).get();
     * }</pre>
     *
     * @param association collection path (may be dotted)
     * @param constraints related predicates; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException  if {@code constraints} is {@code null}
     * @throws IllegalStateException if combined with {@link #page} / {@link #limit}
     * @see #withCollection(String, Consumer)
     */
    public FluentQuery<T> fetchCollection(String association, Consumer<RelatedFilter> constraints) {
        Objects.requireNonNull(constraints, "constraints");
        Values.requireText(association, "association must not be blank");
        String path = association.trim();
        fetchCollection(path);
        fetchOnConstraints.put(path, constraints);
        return this;
    }

    /**
     * Batch collection eager loads mixing plain and constrained entries.
     *
     * <pre>{@code
     * query().fetchCollection(
     *     FetchRel.of("books", f -> f.whereGt("pages", 100)),
     *     FetchRel.of("tags")
     * ).get();
     * }</pre>
     *
     * @param relations one or more {@link FetchRel} specs
     * @return {@code this} for chaining
     * @see #withCollection(FetchRel...)
     */
    public FluentQuery<T> fetchCollection(FetchRel... relations) {
        ensureOpen();
        Objects.requireNonNull(relations, "relations");
        if (relations.length == 0) {
            throw new IllegalArgumentException("fetchCollection requires at least one FetchRel");
        }
        for (FetchRel rel : relations) {
            Objects.requireNonNull(rel, "relation");
            if (rel.constrained()) {
                fetchCollection(rel.path(), rel.constraints());
            } else {
                fetchCollection(rel.path());
            }
        }
        return this;
    }

    /**
     * Eloquent-style eager load for <b>collections</b> (alias of {@link #fetchCollection(String...)}).
     * Same pagination / {@code limit} rules as {@link #fetchCollection(String...)}.
     *
     * @param associations to-many paths
     * @return {@code this} for chaining
     * @see #fetchCollection(String, Consumer)
     */
    public FluentQuery<T> withCollection(String... associations) {
        return fetchCollection(associations);
    }

    /**
     * Alias of {@link #fetchCollection(String, Consumer)} (Eloquent naming).
     *
     * @param association collection path (may be dotted)
     * @param constraints related predicates; must not be {@code null}
     * @return {@code this} for chaining
     */
    public FluentQuery<T> withCollection(String association, Consumer<RelatedFilter> constraints) {
        return fetchCollection(association, constraints);
    }

    /**
     * Alias of {@link #fetchCollection(FetchRel...)}.
     *
     * @param relations fetch specs
     * @return {@code this} for chaining
     */
    public FluentQuery<T> withCollection(FetchRel... relations) {
        return fetchCollection(relations);
    }

    /**
     * Batch collection {@code with} via map ({@code null} consumer = plain). Prefer
     * {@link #fetchCollection(FetchRel...)} to avoid nulls.
     *
     * @param relations path → constraints
     * @return {@code this} for chaining
     */
    public FluentQuery<T> withCollection(
            java.util.Map<String, ? extends Consumer<RelatedFilter>> relations) {
        ensureOpen();
        Objects.requireNonNull(relations, "relations");
        for (var e : relations.entrySet()) {
            if (e.getValue() == null) {
                withCollection(e.getKey());
            } else {
                withCollection(e.getKey(), e.getValue());
            }
        }
        return this;
    }

    /**
     * Same as {@link #withCollection(java.util.Map)} under the {@code fetchCollection} name.
     *
     * @param relations path → constraints
     * @return {@code this} for chaining
     */
    public FluentQuery<T> fetchCollection(
            java.util.Map<String, ? extends Consumer<RelatedFilter>> relations) {
        return withCollection(relations);
    }

    /**
     * Forces {@code DISTINCT} on select queries (also implied by fetch).
     *
     * @return {@code this} for chaining
     */
    public FluentQuery<T> distinct() {
        ensureOpen();
        this.distinct = true;
        return this;
    }

    /**
     * Max rows for {@link #get()} / Spring fluent {@code limit}.
     * {@link #first()} already limits to 1.
     *
     * @param max maximum rows; must be {@code > 0}
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code max <= 0}
     */
    public FluentQuery<T> limit(int max) {
        ensureOpen();
        if (max <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        this.limit = max;
        assertNoCollectionFetchWithLimit();
        return this;
    }

    /**
     * Appends ascending order by the given properties.
     *
     * @param properties attribute names; empty/null → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orderByAsc(String... properties) {
        return orderBy(Sort.Direction.ASC, properties);
    }

    /**
     * Appends descending order by the given properties.
     *
     * @param properties attribute names; empty/null → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orderByDesc(String... properties) {
        return orderBy(Sort.Direction.DESC, properties);
    }

    /**
     * Merges additional {@link Sort} into the builder (appended if already sorted).
     *
     * @param sort sort to merge; {@code null} or unsorted → no-op
     * @return {@code this} for chaining
     */
    public FluentQuery<T> orderBy(Sort sort) {
        ensureOpen();
        if (sort == null || sort.isUnsorted()) {
            return this;
        }
        this.sort = this.sort.isSorted() ? this.sort.and(sort) : sort;
        return this;
    }

    private FluentQuery<T> orderBy(Sort.Direction direction, String... properties) {
        if (properties == null || properties.length == 0) {
            return this;
        }
        return orderBy(Sort.by(direction, properties));
    }

    // -------------------------------------------------------------------------
    // terminals — delegate to Spring Data findBy (no COUNT on first)
    // -------------------------------------------------------------------------

    /**
     * First row ({@code LIMIT 1}) <b>without</b> a COUNT query.
     *
     * <p>When {@link #fetchCollection(String...)} is used, SQL {@code LIMIT} is skipped and the
     * first distinct root is taken in memory (avoids truncating the join product). Prefer a
     * selective {@code where*} so the in-memory set stays small. Filtered collections are a
     * <em>partial</em> view — do not {@code save()} entities with {@code orphanRemoval} after
     * constrained collection fetch.
     *
     * @return first matching entity, or empty
     */
    public Optional<T> first() {
        ensureOpen();
        try {
            // SQL LIMIT + collection JOIN FETCH can truncate the cartesian product before
            // DISTINCT; load without DB limit and take the first root in memory.
            if (!fetchCollections.isEmpty()) {
                List<T> rows = executor.findBy(selectSpec(), q -> configureSortOnly(q).all());
                return rows.stream().findFirst();
            }
            return executor.findBy(selectSpec(), q -> configure(q).limit(1).first());
        } finally {
            markConsumed();
        }
    }

    /**
     * Same as {@link #first()} but throws when empty.
     *
     * <p>Optional sugar — prefer {@link #first()} + {@link Optional} when that fits the host better.
     *
     * @return first matching entity
     * @throws FluentQueryNotFoundException if no row matches
     */
    public T firstOrFail() {
        return first().orElseThrow(
                () -> new FluentQueryNotFoundException("No result found for query"));
    }

    /**
     * Same as {@link #first()} but returns {@code null} instead of empty.
     *
     * @return first matching entity, or {@code null}
     */
    public T firstOrNull() {
        return first().orElse(null);
    }

    /**
     * {@code orderByDesc(property)} + {@link #first()}.
     *
     * @param property sort property (descending)
     * @return latest matching entity, or empty
     */
    public Optional<T> latest(String property) {
        return orderByDesc(property).first();
    }

    /**
     * Same as {@link #latest(String)} but returns {@code null} instead of empty.
     *
     * @param property sort property (descending)
     * @return latest matching entity, or {@code null}
     */
    public T latestOrNull(String property) {
        return latest(property).orElse(null);
    }

    /**
     * {@code orderByAsc(property)} + {@link #first()}.
     *
     * @param property sort property (ascending)
     * @return oldest matching entity, or empty
     */
    public Optional<T> oldest(String property) {
        return orderByAsc(property).first();
    }

    /**
     * Same as {@link #oldest(String)} but returns {@code null} instead of empty.
     *
     * @param property sort property (ascending)
     * @return oldest matching entity, or {@code null}
     */
    public T oldestOrNull(String property) {
        return oldest(property).orElse(null);
    }

    /**
     * Exactly zero or one matching row (Spring Data {@code SpecificationFluentQuery#one()}).
     *
     * <p>Unlike {@link #first()}, this fails when <strong>more than one</strong> row matches
     * ({@link org.springframework.dao.IncorrectResultSizeDataAccessException}).
     * Prefer {@link #first()} when “any / latest” is enough; use {@code one()} when uniqueness
     * is part of the contract.
     *
     * @return the single matching entity, or empty if none
     * @throws org.springframework.dao.IncorrectResultSizeDataAccessException if 2+ rows match
     */
    public Optional<T> one() {
        ensureOpen();
        try {
            if (!fetchCollections.isEmpty()) {
                List<T> rows = executor.findBy(selectSpec(), q -> configureSortOnly(q).all());
                if (rows.size() > 1) {
                    throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1, rows.size());
                }
                return rows.stream().findFirst();
            }
            return executor.findBy(selectSpec(), q -> configure(q).one());
        } finally {
            markConsumed();
        }
    }

    /**
     * Same as {@link #one()} but throws when empty.
     *
     * <p>Optional sugar — prefer {@link #one()} + {@link Optional} when that fits the host better.
     *
     * @return the single matching entity
     * @throws FluentQueryNotFoundException if no row matches
     */
    public T oneOrFail() {
        return one().orElseThrow(
                () -> new FluentQueryNotFoundException("No result found for query"));
    }

    /**
     * Same as {@link #one()} but returns {@code null} instead of empty.
     *
     * @return the single matching entity, or {@code null}
     */
    public T oneOrNull() {
        return one().orElse(null);
    }

    /**
     * All matching rows (respects {@link #limit(int)}).
     * Without a limit this can be expensive — prefer {@link #page(Pageable)} / {@link #slice(Pageable)}.
     *
     * @return matching entities
     */
    public List<T> get() {
        ensureOpen();
        assertNoCollectionFetchWithLimit();
        try {
            return executor.findBy(selectSpec(), q -> configure(q).all());
        } finally {
            markConsumed();
        }
    }

    /**
     * Page with COUNT. Do not combine with {@link #fetchCollection(String...)}.
     *
     * @param pageable page request; {@code null} uses defaults from {@link #limit(int)} / sort
     * @return a page of results
     * @throws IllegalStateException if {@link #fetchCollection(String...)} was used
     */
    public Page<T> page(Pageable pageable) {
        ensureOpen();
        assertNoCollectionFetchWithPagination();
        Pageable effective = resolvePageable(pageable);
        try {
            return executor.findBy(selectSpec(), q -> configureSortOnly(q).page(effective));
        } finally {
            markConsumed();
        }
    }

    /**
     * Eloquent-style alias for {@link #page(Pageable)} using a 0-based {@code page} index.
     *
     * @param page 0-based page index
     * @param size page size ({@code > 0})
     * @return a page of results
     * @throws IllegalArgumentException if {@code page < 0} or {@code size <= 0}
     * @throws IllegalStateException    if {@link #fetchCollection(String...)} was used
     */
    public Page<T> paginate(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        return page(PageRequest.of(page, size, sort));
    }

    /**
     * Slice <b>without</b> COUNT (better for infinite scroll).
     * Do not combine with {@link #fetchCollection(String...)}.
     *
     * @param pageable page request; {@code null} uses defaults from {@link #limit(int)} / sort
     * @return a slice of results
     * @throws IllegalStateException if {@link #fetchCollection(String...)} was used
     */
    public Slice<T> slice(Pageable pageable) {
        ensureOpen();
        assertNoCollectionFetchWithPagination();
        Pageable effective = resolvePageable(pageable);
        try {
            return executor.findBy(selectSpec(), q -> configureSortOnly(q).slice(effective));
        } finally {
            markConsumed();
        }
    }

    /**
     * Processes results in batches (Eloquent {@code chunk}). Uses {@link #slice(Pageable)} (no COUNT).
     * Stable ordering is recommended ({@link #orderByAsc(String...)} / {@link #orderByDesc(String...)}).
     *
     * @param size     batch size ({@code > 0})
     * @param consumer receives each non-empty batch
     * @throws NullPointerException     if {@code consumer} is {@code null}
     * @throws IllegalArgumentException if {@code size <= 0}
     * @throws IllegalStateException    if {@link #fetchCollection(String...)} was used
     */
    public void chunk(int size, Consumer<List<T>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        ensureOpen();
        assertNoCollectionFetchWithPagination();
        try {
            int pageIndex = 0;
            Slice<T> batch;
            do {
                Pageable pageable = PageRequest.of(pageIndex++, size, sort);
                batch = executor.findBy(selectSpec(), q -> configureSortOnly(q).slice(pageable));
                if (batch.hasContent()) {
                    consumer.accept(batch.getContent());
                }
            } while (batch.hasNext());
        } finally {
            markConsumed();
        }
    }

    /**
     * Stream of results; <b>must be closed</b> ({@code try-with-resources}).
     *
     * @return a closeable stream of matching entities
     */
    public Stream<T> stream() {
        ensureOpen();
        assertNoCollectionFetchWithLimit();
        markConsumed();
        return executor.findBy(selectSpec(), q -> configure(q).stream());
    }

    /**
     * Interface/DTO projection (Spring Data {@code as}). No entity join-fetch;
     * uses predicates + sort/limit. Combine with {@link #select(String...)} to limit
     * projected properties (recommended for lean SQL).
     *
     * <p>Fails fast if {@link #fetch}/{@link #fetchCollection} were configured —
     * projections do not apply those joins; use {@link #first()}/{@link #get()}/{@link #page}
     * for entity fetches, or {@code select(...).firstAs(...)} without fetch.
     *
     * @param <R>            projection type
     * @param projectionType interface or DTO class
     * @return first projected result, or empty
     * @throws NullPointerException     if {@code projectionType} is {@code null}
     * @throws IllegalStateException    if join fetches were configured
     */
    public <R> Optional<R> firstAs(Class<R> projectionType) {
        Objects.requireNonNull(projectionType, "projectionType");
        ensureNoFetchesForProjection("firstAs");
        ensureOpen();
        try {
            return executor.findBy(predicateSpec(), q -> configure(q.as(projectionType)).limit(1).first());
        } finally {
            markConsumed();
        }
    }

    /**
     * All rows as a projection (respects {@link #limit(int)}).
     *
     * @param <R>            projection type
     * @param projectionType interface or DTO class
     * @return projected results
     * @throws NullPointerException if {@code projectionType} is {@code null}
     */
    public <R> List<R> getAs(Class<R> projectionType) {
        Objects.requireNonNull(projectionType, "projectionType");
        ensureNoFetchesForProjection("getAs");
        ensureOpen();
        try {
            return executor.findBy(predicateSpec(), q -> configure(q.as(projectionType)).all());
        } finally {
            markConsumed();
        }
    }

    /**
     * Page of projections with COUNT.
     *
     * @param <R>            projection type
     * @param projectionType interface or DTO class
     * @param pageable       page request; {@code null} uses defaults
     * @return a page of projected results
     * @throws NullPointerException if {@code projectionType} is {@code null}
     */
    public <R> Page<R> pageAs(Class<R> projectionType, Pageable pageable) {
        Objects.requireNonNull(projectionType, "projectionType");
        ensureNoFetchesForProjection("pageAs");
        ensureOpen();
        Pageable effective = resolvePageable(pageable);
        try {
            return executor.findBy(predicateSpec(), q -> configureSortOnly(q.as(projectionType)).page(effective));
        } finally {
            markConsumed();
        }
    }

    /**
     * Existence check — predicates only (no fetch).
     *
     * @return {@code true} if at least one row matches
     */
    public boolean exists() {
        ensureOpen();
        try {
            return executor.exists(predicateSpec());
        } finally {
            markConsumed();
        }
    }

    /**
     * Count — predicates only (no fetch).
     *
     * @return number of matching rows
     */
    public long count() {
        ensureOpen();
        try {
            return executor.count(predicateSpec());
        } finally {
            markConsumed();
        }
    }

    /**
     * Deletes all entities matching the current predicates (Eloquent-style bulk delete).
     *
     * <p>Loads matching rows then calls {@link CrudRepository#deleteAll(Iterable)}. Prefer a
     * restrictive {@code where*} / {@code limit} for large tables. The underlying executor must
     * also implement {@link CrudRepository} (true for {@link FluentQueryRepository}).
     *
     * <p>When repositories use {@link dev.benjaminor.fluentquery.lifecycle.FluentQueryJpaRepository}
     * with lifecycle enabled, each loaded entity goes through {@code onDeleting} / {@code onDeleted}
     * (unlike {@code deleteAllInBatch}, which skips hooks).
     *
     * @return number of deleted entities
     * @throws IllegalStateException if the executor is not a {@link CrudRepository}
     */
    @SuppressWarnings("unchecked")
    public long delete() {
        ensureOpen();
        if (!(executor instanceof CrudRepository<?, ?> crud)) {
            throw new IllegalStateException(
                    "FluentQuery.delete() requires a CrudRepository executor "
                            + "(e.g. FluentQueryRepository).");
        }
        try {
            // Predicates only — avoid JOIN FETCH graphs on bulk delete loads.
            List<T> rows = executor.findBy(predicateSpec(), q -> configure(q).all());
            if (rows.isEmpty()) {
                return 0L;
            }
            ((CrudRepository<T, ?>) crud).deleteAll(rows);
            return rows.size();
        } finally {
            markConsumed();
        }
    }

    /**
     * Predicate {@link Specification} (no fetch). Never {@code null}.
     *
     * @return composed predicate spec
     */
    public Specification<T> toSpecification() {
        return predicateSpec();
    }

    /**
     * Select {@link Specification} (with fetch/distinct when applicable).
     *
     * @return select spec including fetch joins when configured
     */
    public Specification<T> toSelectSpecification() {
        return selectSpec();
    }

    /**
     * Accumulated {@link Sort} (may be unsorted).
     *
     * @return current sort
     */
    public Sort toSort() {
        return sort;
    }

    // -------------------------------------------------------------------------
    // internals
    // -------------------------------------------------------------------------

    private <S> SpecificationFluentQuery<S> configure(SpecificationFluentQuery<S> q) {
        SpecificationFluentQuery<S> result = configureSortOnly(q);
        if (limit != null) {
            result = result.limit(limit);
        }
        return result;
    }

    private <S> SpecificationFluentQuery<S> configureSortOnly(SpecificationFluentQuery<S> q) {
        SpecificationFluentQuery<S> result = q;
        if (!selectColumns.isEmpty()) {
            result = result.project(selectColumns);
        }
        if (sort.isSorted()) {
            result = result.sortBy(sort);
        }
        return result;
    }

    private Pageable resolvePageable(Pageable pageable) {
        if (pageable == null) {
            int size = limit != null ? limit : 20;
            return PageRequest.of(0, size, sort);
        }
        if (pageable.getSort().isUnsorted() && sort.isSorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }
        return pageable;
    }

    private void assertNoCollectionFetchWithPagination() {
        if (!fetchCollections.isEmpty()) {
            throw new IllegalStateException(
                    "FluentQuery: fetchCollection(...) is not compatible with page()/slice() "
                            + "(cartesian product / incorrect COUNT). "
                            + "Use fetch() for to-one only, or load collections in a second query.");
        }
    }

    private void assertNoCollectionFetchWithLimit() {
        if (!fetchCollections.isEmpty() && limit != null) {
            throw new IllegalStateException(
                    "FluentQuery: fetchCollection(...) is not compatible with limit() "
                            + "(SQL LIMIT applies to the join product before DISTINCT). "
                            + "Omit limit(), or load collections in a second query.");
        }
    }

    private void ensureOpen() {
        if (consumed) {
            throw new IllegalStateException(
                    "FluentQuery builder already executed; create a new query() / FluentQuery.of(...)");
        }
    }

    private void markConsumed() {
        this.consumed = true;
    }

    /** Predicates only (count/exists/projections). */
    private Specification<T> predicateSpec() {
        return spec != null ? spec : alwaysTrue();
    }

    /** Select with fetch/distinct. */
    private Specification<T> selectSpec() {
        Specification<T> base = predicateSpec();
        if (fetchToOne.isEmpty() && fetchCollections.isEmpty() && !distinct) {
            return base;
        }
        return (root, query, cb) -> {
            if (!isCountQuery(query)) {
                if (distinct || !fetchToOne.isEmpty() || !fetchCollections.isEmpty()) {
                    query.distinct(true);
                }
                for (String assoc : fetchToOne) {
                    Fetches.fetchPathConstrained(
                            root, cb, assoc, JoinType.LEFT, fetchOnConstraints.get(assoc));
                }
                for (String assoc : fetchCollections) {
                    Fetches.fetchPathConstrained(
                            root, cb, assoc, JoinType.LEFT, fetchOnConstraints.get(assoc));
                }
            }
            return base.toPredicate(root, query, cb);
        };
    }

    private static boolean isCountQuery(jakarta.persistence.criteria.CriteriaQuery<?> query) {
        if (query == null || query.getResultType() == null) {
            return false;
        }
        Class<?> resultType = query.getResultType();
        return Number.class.isAssignableFrom(resultType)
                || resultType.equals(Long.class)
                || resultType.equals(long.class)
                || resultType.equals(Integer.class)
                || resultType.equals(int.class);
    }

    private Specification<T> strictEqualSpec(String column, Object normalized) {
        return (root, query, cb) -> cb.equal(root.get(column), normalized);
    }

    private Specification<T> strictNotEqualSpec(String column, Object normalized) {
        return (root, query, cb) -> cb.notEqual(root.get(column), normalized);
    }

    private Specification<T> likeSpec(String column, String value) {
        String raw = value == null ? "" : value;
        if (filters != null && Values.hasText(raw)) {
            return filters.hasPropertyLike(column, raw);
        }
        String pattern = LikePatterns.toPattern(raw);
        return (root, query, cb) -> cb.like(LikeExpressions.of(root, cb, column), pattern);
    }

    private Specification<T> relatedLikeSpec(String relation, String column, String value) {
        String raw = value == null ? "" : value;
        if (filters != null && Values.hasText(raw)) {
            return filters.hasRelatedPropertyLike(relation, column, raw);
        }
        String pattern = LikePatterns.toPattern(raw);
        return (root, query, cb) -> {
            Join<?, ?> join = Joins.joinPath(root, relation, JoinType.INNER);
            return cb.like(LikeExpressions.of(join, cb, column), pattern);
        };
    }

    /** Escaped LIKE (literal {@code %}/{@code _} in the value); escape char {@code \\}. */
    private Specification<T> likeEscapedSpec(String column, String pattern) {
        if (filters != null) {
            return filters.hasPropertyLikeEscaped(column, pattern);
        }
        return (root, query, cb) -> cb.like(LikeExpressions.of(root, cb, column), pattern, '\\');
    }

    /** Raw LIKE pattern (caller supplies {@code %}/{@code _}); no escape char. */
    private Specification<T> likeRawSpec(String column, String pattern) {
        if (filters != null) {
            return filters.hasPropertyLikePattern(column, pattern);
        }
        return (root, query, cb) -> cb.like(LikeExpressions.of(root, cb, column), pattern);
    }

    private Specification<T> inSpec(String column, Collection<?> values) {
        if (filters != null) {
            return filters.hasPropertyIn(column, values);
        }
        return (root, query, cb) -> root.get(column).in(values);
    }

    private Specification<T> notInSpec(String column, Collection<?> values) {
        if (filters != null) {
            return filters.hasPropertyNotIn(column, values);
        }
        return (root, query, cb) -> cb.not(root.get(column).in(values));
    }

    private <Y extends Comparable<? super Y>> Specification<T> betweenSpec(String column, Y from, Y to) {
        if (filters != null) {
            return filters.hasPropertyBetween(column, from, to);
        }
        return (root, query, cb) -> {
            var path = root.<Y>get(column);
            if (from != null && to != null) {
                return cb.between(path, from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(path, from);
            }
            return cb.lessThanOrEqualTo(path, to);
        };
    }

    private <Y extends Comparable<? super Y>> Specification<T> notBetweenSpec(String column, Y from, Y to) {
        return (root, query, cb) -> cb.not(cb.between(root.<Y>get(column), from, to));
    }

    private Specification<T> dateEqualsSpec(String column, LocalDate date) {
        return (root, query, cb) -> {
            Path<?> path = root.get(column);
            return cb.and(
                    cb.equal(datePart(cb, path, "year"), date.getYear()),
                    cb.equal(datePart(cb, path, "month"), date.getMonthValue()),
                    cb.equal(datePart(cb, path, "day"), date.getDayOfMonth()));
        };
    }

    private Specification<T> timeEqualsSpec(String column, LocalTime time) {
        return (root, query, cb) -> {
            Path<?> path = root.get(column);
            return cb.and(
                    cb.equal(datePart(cb, path, "hour"), time.getHour()),
                    cb.equal(datePart(cb, path, "minute"), time.getMinute()),
                    cb.equal(datePart(cb, path, "second"), time.getSecond()));
        };
    }

    private Specification<T> datePartEqualsSpec(String column, String function, int value) {
        return (root, query, cb) -> cb.equal(datePart(cb, root.get(column), function), value);
    }

    private static Expression<Integer> datePart(CriteriaBuilder cb, Path<?> path, String function) {
        return cb.function(function, Integer.class, path);
    }

    private static void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
    }

    private static void validateDay(int day) {
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("day must be between 1 and 31");
        }
    }

    private static String normalizeColumnOperator(String operator) {
        Objects.requireNonNull(operator, "operator");
        String op = operator.trim();
        if ("<>".equals(op)) {
            op = "!=";
        }
        return switch (op) {
            case "=", "!=", ">", ">=", "<", "<=" -> op;
            default -> throw new IllegalArgumentException(
                    "Invalid column comparison operator: " + operator
                            + " (supported: =, !=, <>, >, >=, <, <=)");
        };
    }

    private Specification<T> columnCompareSpec(String left, String operator, String right) {
        return (root, query, cb) -> comparePaths(cb, root.get(left), root.get(right), operator);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate comparePaths(
            CriteriaBuilder cb, Path<?> left, Path<?> right, String operator) {
        return switch (operator) {
            case "=" -> cb.equal(left, right);
            case "!=" -> cb.notEqual(left, right);
            case ">" -> cb.greaterThan((Expression) left, (Expression) right);
            case ">=" -> cb.greaterThanOrEqualTo((Expression) left, (Expression) right);
            case "<" -> cb.lessThan((Expression) left, (Expression) right);
            case "<=" -> cb.lessThanOrEqualTo((Expression) left, (Expression) right);
            default -> throw new IllegalArgumentException(
                    "Invalid column comparison operator: " + operator);
        };
    }

    private Specification<T> hasRelationSpec(String relation) {
        return (root, query, cb) -> {
            var attr = root.getModel().getAttribute(relation);
            if (attr.isCollection()) {
                return cb.isNotEmpty(root.get(relation));
            }
            return cb.isNotNull(root.get(relation));
        };
    }

    private Specification<T> doesntHaveRelationSpec(String relation) {
        return (root, query, cb) -> {
            var attr = root.getModel().getAttribute(relation);
            if (attr.isCollection()) {
                return cb.isEmpty(root.get(relation));
            }
            return cb.isNull(root.get(relation));
        };
    }

    private FluentQuery<T> whereExists(
            String relation, Consumer<RelatedFilter> nested, boolean negate) {
        return where(existsSpec(relation, nested, negate));
    }

    private FluentQuery<T> orWhereExists(
            String relation, Consumer<RelatedFilter> nested, boolean negate) {
        return orWhere(existsSpec(relation, nested, negate));
    }

    private Specification<T> existsSpec(
            String relation, Consumer<RelatedFilter> nested, boolean negate) {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(nested, "nested");
        RelatedFilter filter = new RelatedFilter();
        nested.accept(filter);
        return (root, query, cb) -> {
            Objects.requireNonNull(query, "query");
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<T> correlated = sq.correlate(root);
            Join<?, ?> join = Joins.joinPath(correlated, relation, JoinType.INNER);
            sq.select(cb.literal(1));
            Predicate p = filter.toPredicate(join, cb);
            if (p != null) {
                sq.where(p);
            }
            Predicate exists = cb.exists(sq);
            return negate ? cb.not(exists) : exists;
        };
    }

    /** {@code EXISTS}/{@code NOT EXISTS} for a dotted (or single) association path with no leaf filter. */
    private Specification<T> existsPathSpec(String relation, boolean negate) {
        return existsSpec(relation, f -> {
        }, negate);
    }

    private static String attrName(Attribute<?, ?> attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return attribute.getName();
    }

    private static String[] attrNames(Attribute<?, ?>... attributes) {
        if (attributes == null || attributes.length == 0) {
            return new String[0];
        }
        String[] names = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            names[i] = attrName(attributes[i]);
        }
        return names;
    }

    private Specification<T> buildGroupSpec(Consumer<FluentQuery<T>> group) {
        if (group == null) {
            return null;
        }
        FluentQuery<T> nested = new FluentQuery<>(executor, filters);
        group.accept(nested);
        if (!nested.fetchToOne.isEmpty()
                || !nested.fetchCollections.isEmpty()
                || !nested.fetchOnConstraints.isEmpty()
                || nested.sort.isSorted()
                || nested.limit != null
                || !nested.selectColumns.isEmpty()
                || nested.distinct) {
            throw new IllegalStateException(
                    "FluentQuery groups (where/orWhere with a Consumer) only support nested "
                            + "predicates. Do not call fetch*/with*, orderBy*, limit, select, or distinct "
                            + "inside a group.");
        }
        return nested.spec;
    }

    private Specification<T> alwaysTrue() {
        return (root, query, cb) -> cb.conjunction();
    }

    private void ensureNoFetchesForProjection(String terminal) {
        if (!fetchToOne.isEmpty() || !fetchCollections.isEmpty()) {
            throw new IllegalStateException(
                    terminal + " ignores join fetches; remove fetch()/fetchCollection()/with*() "
                            + "or use first()/get()/page() for entity results with eager loads. "
                            + "For lean projections, use select(...) without fetch.");
        }
    }

    private void addFetches(List<String> target, List<String> conflicting, String... associations) {
        if (associations == null) {
            return;
        }
        for (String a : associations) {
            if (!Values.hasText(a)) {
                continue;
            }
            String name = a.trim();
            if (name.indexOf(':') >= 0) {
                throw new IllegalArgumentException(
                        "fetch/fetchCollection does not support column lists (got '" + a + "'). "
                                + "JOIN FETCH always loads the full association. "
                                + "Use select(\"" + a + "\") with getAs/firstAs for a lean projection, "
                                + "or fetch(\"" + name.substring(0, name.indexOf(':')).trim()
                                + "\") to eager-load the whole association.");
            }
            if (conflicting.contains(name)) {
                throw new IllegalStateException(
                        "FluentQuery: path '" + name + "' cannot be used with both fetch() and "
                                + "fetchCollection(). Pick to-one or to-many.");
            }
            if (!target.contains(name)) {
                target.add(name);
            }
            // Plain (re)fetch clears any prior ON constraint for this path.
            fetchOnConstraints.remove(name);
        }
    }

    /** Requires a string-typed attribute; non-string columns fail at Criteria runtime. */
    private Specification<T> equalIgnoreCaseSpec(String column, String upperNormalized) {
        return (root, query, cb) -> cb.equal(cb.upper(root.get(column)), upperNormalized);
    }

    private static Object normalizeEqualValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return Values.trimToNull(s);
        }
        return value;
    }
}
