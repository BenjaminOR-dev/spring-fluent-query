package dev.benjaminor.fluentquery;

import dev.benjaminor.fluentquery.support.LikeExpressions;
import dev.benjaminor.fluentquery.support.LikePatterns;
import dev.benjaminor.fluentquery.support.Values;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Small fluent builder for predicates on a related {@link From}/{@code Join}, typically used
 * inside nested {@link FluentQuery#whereHas(String, java.util.function.Consumer)} /
 * {@link FluentQuery#whereDoesntHave(String, java.util.function.Consumer)} ({@code EXISTS}).
 *
 * <p>Strict methods always apply a predicate ({@code null} equality → {@code IS NULL}, same
 * semantics as {@link FluentQuery}). Optional methods skip when the value is blank/empty.
 * Nested {@code LIKE} respects {@link FluentQueryDefaults#likeMode()}.
 * Supports {@code whereColumn}, {@code whereEqualIgnoreCase}, {@code orWhere*}, and {@code when}/{@code unless}.
 * Nested attribute paths like {@code "a.b"} are <em>not</em> resolved here — use
 * {@link FluentQuery#whereRelatedEqual}, {@link FluentQuery#whereRelation}, or
 * {@link FluentQuery#whereHas} on the root builder.
 *
 * <p>Also used as the constraint builder for constrained eager loads
 * ({@link FluentQuery#fetch(String, java.util.function.Consumer)} /
 * {@link FluentQuery#fetchCollection(String, java.util.function.Consumer)}), where predicates
 * become the leaf join {@code ON} clause (not a root {@code WHERE}).
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * authorRepository.query()
 *     .whereHas("books", f -> f.whereGt("pages", 100).whereLike("title", "Spring"))
 *     .get();
 *
 * authorRepository.query()
 *     .fetchCollection("books", f -> f.whereGt("pages", 100))
 *     .get();
 * }</pre>
 *
 * @see FluentQuery#whereHas(String, java.util.function.Consumer)
 * @see FluentQuery#whereDoesntHave(String, java.util.function.Consumer)
 * @see FluentQuery#fetch(String, java.util.function.Consumer)
 */
public final class RelatedFilter {

    private BiFunction<From<?, ?>, CriteriaBuilder, Predicate> tree = null;

    /**
     * {@code true} when no predicates were added.
     *
     * @return whether this filter is empty
     */
    public boolean isEmpty() {
        return tree == null;
    }

    /**
     * Strict equality. {@code null} → {@code IS NULL}.
     *
     * @param column attribute name on the related entity
     * @param value  expected value; {@code null} means {@code IS NULL}
     * @return {@code this} for chaining
     */
    public RelatedFilter where(String column, Object value) {
        return whereEqual(column, value);
    }

    /**
     * Strict equality. {@code null} → {@code IS NULL}.
     *
     * @param column attribute name on the related entity
     * @param value  expected value; {@code null} means {@code IS NULL}
     * @return {@code this} for chaining
     */
    public RelatedFilter whereEqual(String column, Object value) {
        Objects.requireNonNull(column, "column");
        if (value == null) {
            return whereNull(column);
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        andPart((from, cb) -> cb.equal(from.get(column), normalized));
        return this;
    }

    /**
     * Case-insensitive equality ({@code UPPER(col) = value}) with {@link Locale#ROOT}
     * on the Java side. Intended for string columns only.
     *
     * @param column attribute name on the related entity
     * @param value  expected value; {@code null} → {@link #whereNull(String)}
     * @return {@code this} for chaining
     */
    public RelatedFilter whereEqualIgnoreCase(String column, String value) {
        Objects.requireNonNull(column, "column");
        if (value == null) {
            return whereNull(column);
        }
        String normalized = Values.trimToNullUpper(value);
        String upper = normalized == null ? "" : normalized;
        return andPart((from, cb) -> cb.equal(cb.upper(from.get(column)), upper));
    }

    /**
     * Case-insensitive equality only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name
     * @param value  expected value; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereEqualIgnoreCase(String column, String value) {
        String normalized = Values.trimToNullUpper(value);
        return normalized == null ? this : whereEqualIgnoreCase(column, normalized);
    }

    /**
     * Column-to-column equality on the related entity ({@code left = right}).
     *
     * @param left  left attribute
     * @param right right attribute
     * @return {@code this} for chaining
     */
    public RelatedFilter whereColumn(String left, String right) {
        return whereColumn(left, "=", right);
    }

    /**
     * Column-to-column comparison. Operators: {@code =}, {@code !=}, {@code <>}, {@code >},
     * {@code >=}, {@code <}, {@code <=}.
     *
     * @param left     left attribute
     * @param operator comparison operator
     * @param right    right attribute
     * @return {@code this} for chaining
     */
    public RelatedFilter whereColumn(String left, String operator, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        String op = normalizeColumnOperator(operator);
        return andPart((from, cb) -> comparePaths(cb, from.get(left), from.get(right), op));
    }

    /**
     * Runs {@code consumer} only when {@code condition} is {@code true}.
     *
     * @param condition when {@code false}, no-op
     * @param consumer  mutations; {@code null} ignored
     * @return {@code this} for chaining
     */
    public RelatedFilter when(boolean condition, Consumer<RelatedFilter> consumer) {
        if (condition && consumer != null) {
            consumer.accept(this);
        }
        return this;
    }

    /**
     * Runs {@code consumer} only when {@code condition} is {@code false}.
     *
     * @param condition when {@code true}, no-op
     * @param consumer  mutations; {@code null} ignored
     * @return {@code this} for chaining
     */
    public RelatedFilter unless(boolean condition, Consumer<RelatedFilter> consumer) {
        return when(!condition, consumer);
    }

    /**
     * OR equality. {@code null} → {@code IS NULL}.
     *
     * @param column attribute name
     * @param value  expected value
     * @return {@code this} for chaining
     */
    public RelatedFilter orWhere(String column, Object value) {
        Objects.requireNonNull(column, "column");
        if (value == null) {
            return orPart((from, cb) -> cb.isNull(from.get(column)));
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        return orPart((from, cb) -> cb.equal(from.get(column), normalized));
    }

    /**
     * OR group: {@code orWhere(f -> f.where(...).where(...))} → {@code … OR (a AND b)}.
     *
     * @param group nested filter consumer
     * @return {@code this} for chaining
     */
    public RelatedFilter orWhere(Consumer<RelatedFilter> group) {
        if (group == null) {
            return this;
        }
        RelatedFilter nested = new RelatedFilter();
        group.accept(nested);
        // Empty nested filter → conjunction (TRUE); OR TRUE would make the whole
        // predicate always-true. Ignore like FluentQuery groups.
        if (nested.isEmpty()) {
            return this;
        }
        return orPart(nested::toPredicate);
    }

    /**
     * AND group on the related entity.
     *
     * @param group nested filter consumer
     * @return {@code this} for chaining
     */
    public RelatedFilter where(Consumer<RelatedFilter> group) {
        if (group == null) {
            return this;
        }
        RelatedFilter nested = new RelatedFilter();
        group.accept(nested);
        // Empty nested filter → conjunction (TRUE); skip no-op AND TRUE.
        if (nested.isEmpty()) {
            return this;
        }
        return andPart(nested::toPredicate);
    }

    /**
     * Strict inequality. {@code null} → {@code IS NOT NULL}.
     *
     * @param column attribute name on the related entity
     * @param value  forbidden value; {@code null} means {@code IS NOT NULL}
     * @return {@code this} for chaining
     */
    public RelatedFilter whereNotEqual(String column, Object value) {
        Objects.requireNonNull(column, "column");
        if (value == null) {
            return whereNotNull(column);
        }
        Object normalized = value instanceof String s ? Values.trimToEmpty(s) : value;
        andPart((from, cb) -> cb.notEqual(from.get(column), normalized));
        return this;
    }

    /**
     * Case-insensitive {@code LIKE} (strict; non-blank value required). Same pattern rules as
     * {@link FluentQuery#whereLike(String, String)} ({@link LikePatterns#toPattern(String)}).
     *
     * @param column attribute name on the related entity
     * @param value  substring or LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public RelatedFilter whereLike(String column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereLike for search params");
        String pattern = LikePatterns.toPattern(value);
        andPart((from, cb) -> cb.like(LikeExpressions.of(from, cb, column), pattern));
        return this;
    }

    /**
     * Escaped contains match; {@code %} / {@code _} in {@code value} are literal.
     *
     * @param column attribute name on the related entity
     * @param value  free-text substring; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public RelatedFilter whereContains(String column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereContains for search params");
        String pattern = LikePatterns.containsEscaped(value);
        andPart((from, cb) -> cb.like(LikeExpressions.of(from, cb, column), pattern, '\\'));
        return this;
    }

    /**
     * Escaped prefix match ({@code VALUE%}).
     *
     * @param column attribute name on the related entity
     * @param value  prefix; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public RelatedFilter whereStartsWith(String column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereStartsWith for search params");
        String pattern = LikePatterns.startsWithEscaped(value);
        andPart((from, cb) -> cb.like(LikeExpressions.of(from, cb, column), pattern, '\\'));
        return this;
    }

    /**
     * Escaped suffix match ({@code %VALUE}).
     *
     * @param column attribute name on the related entity
     * @param value  suffix; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public RelatedFilter whereEndsWith(String column, String value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        Values.requireText(value, "value must not be blank; use optionalWhereEndsWith for search params");
        String pattern = LikePatterns.endsWithEscaped(value);
        andPart((from, cb) -> cb.like(LikeExpressions.of(from, cb, column), pattern, '\\'));
        return this;
    }

    /**
     * Raw {@code LIKE} pattern (only trimmed + upper-cased). Supply {@code %}/{@code _} yourself.
     *
     * @param column  attribute name on the related entity
     * @param pattern raw LIKE pattern; must not be {@code null} or blank
     * @return {@code this} for chaining
     * @throws NullPointerException     if {@code pattern} is {@code null}
     * @throws IllegalArgumentException if {@code pattern} is blank
     */
    public RelatedFilter whereLikePattern(String column, String pattern) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(pattern, "pattern");
        Values.requireText(pattern, "pattern must not be blank; use optionalWhereLikePattern for search params");
        String upper = Values.trimToEmpty(pattern).toUpperCase(java.util.Locale.ROOT);
        andPart((from, cb) -> cb.like(LikeExpressions.of(from, cb, column), upper));
        return this;
    }

    /**
     * Strict {@code IN}. Empty collection → never-matches ({@code disjunction}).
     *
     * @param column attribute name on the related entity
     * @param values allowed values; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public RelatedFilter whereIn(String column, Collection<?> values) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            andPart((from, cb) -> cb.disjunction());
        } else {
            andPart((from, cb) -> from.get(column).in(values));
        }
        return this;
    }

    /**
     * Strict {@code NOT IN}. Empty collection → always-true ({@code conjunction}).
     *
     * @param column attribute name on the related entity
     * @param values forbidden values; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code values} is {@code null}
     */
    public RelatedFilter whereNotIn(String column, Collection<?> values) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            andPart((from, cb) -> cb.conjunction());
        } else {
            andPart((from, cb) -> cb.not(from.get(column).in(values)));
        }
        return this;
    }

    /**
     * {@code column IS NULL}.
     *
     * @param column attribute name on the related entity
     * @return {@code this} for chaining
     */
    public RelatedFilter whereNull(String column) {
        Objects.requireNonNull(column, "column");
        andPart((from, cb) -> cb.isNull(from.get(column)));
        return this;
    }

    /**
     * {@code column IS NOT NULL}.
     *
     * @param column attribute name on the related entity
     * @return {@code this} for chaining
     */
    public RelatedFilter whereNotNull(String column) {
        Objects.requireNonNull(column, "column");
        andPart((from, cb) -> cb.isNotNull(from.get(column)));
        return this;
    }

    /**
     * Alias for {@link #whereGreaterThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereGt(String column, Y value) {
        return whereGreaterThan(column, value);
    }

    /**
     * Strict {@code column > value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereGreaterThan(String column, Y value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        andPart((from, cb) -> cb.greaterThan(from.get(column), value));
        return this;
    }

    /**
     * Alias for {@link #whereGreaterThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereGte(String column, Y value) {
        return whereGreaterThanOrEqualTo(column, value);
    }

    /**
     * Strict {@code column >= value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive lower bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereGreaterThanOrEqualTo(
            String column, Y value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        andPart((from, cb) -> cb.greaterThanOrEqualTo(from.get(column), value));
        return this;
    }

    /**
     * Alias for {@link #whereLessThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereLt(String column, Y value) {
        return whereLessThan(column, value);
    }

    /**
     * Strict {@code column < value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereLessThan(String column, Y value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        andPart((from, cb) -> cb.lessThan(from.get(column), value));
        return this;
    }

    /**
     * Alias for {@link #whereLessThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereLte(String column, Y value) {
        return whereLessThanOrEqualTo(column, value);
    }

    /**
     * Strict {@code column <= value}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereLessThanOrEqualTo(
            String column, Y value) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(value, "value");
        andPart((from, cb) -> cb.lessThanOrEqualTo(from.get(column), value));
        return this;
    }

    /**
     * Inclusive range. If only one bound is non-null, only that bound is applied.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param from   inclusive lower bound; may be {@code null} if {@code to} is set
     * @param to     inclusive upper bound; may be {@code null} if {@code from} is set
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if both bounds are {@code null}
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereBetween(String column, Y from, Y to) {
        Objects.requireNonNull(column, "column");
        if (from == null && to == null) {
            throw new IllegalArgumentException("whereBetween: from and to cannot both be null");
        }
        andPart((pathFrom, cb) -> {
            var path = pathFrom.<Y>get(column);
            if (from != null && to != null) {
                return cb.between(path, from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(path, from);
            }
            return cb.lessThanOrEqualTo(path, to);
        });
        return this;
    }

    /**
     * Negated inclusive range. Both bounds required.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param from   inclusive lower bound; must not be {@code null}
     * @param to     inclusive upper bound; must not be {@code null}
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter whereNotBetween(String column, Y from, Y to) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        andPart((pathFrom, cb) -> cb.not(cb.between(pathFrom.<Y>get(column), from, to)));
        return this;
    }

    /**
     * Matches calendar date ({@code year} AND {@code month} AND {@code day}) via portable
     * Hibernate {@code cb.function} names.
     *
     * @param column temporal attribute name
     * @param date   calendar date; must not be {@code null}
     * @return {@code this} for chaining
     */
    public RelatedFilter whereDate(String column, LocalDate date) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(date, "date");
        andPart((from, cb) -> {
            Path<?> path = from.get(column);
            return cb.and(
                    cb.equal(datePart(cb, path, "year"), date.getYear()),
                    cb.equal(datePart(cb, path, "month"), date.getMonthValue()),
                    cb.equal(datePart(cb, path, "day"), date.getDayOfMonth()));
        });
        return this;
    }

    /**
     * Matches the year part of a temporal column.
     *
     * @param column temporal attribute name
     * @param year   calendar year
     * @return {@code this} for chaining
     */
    public RelatedFilter whereYear(String column, int year) {
        Objects.requireNonNull(column, "column");
        andPart((from, cb) -> cb.equal(datePart(cb, from.get(column), "year"), year));
        return this;
    }

    /**
     * Matches the month part ({@code 1}–{@code 12}).
     *
     * @param column temporal attribute name
     * @param month  month of year
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code month} is outside {@code 1}–{@code 12}
     */
    public RelatedFilter whereMonth(String column, int month) {
        Objects.requireNonNull(column, "column");
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        andPart((from, cb) -> cb.equal(datePart(cb, from.get(column), "month"), month));
        return this;
    }

    /**
     * Matches the day-of-month part ({@code 1}–{@code 31}).
     *
     * @param column temporal attribute name
     * @param day    day of month
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if {@code day} is outside {@code 1}–{@code 31}
     */
    public RelatedFilter whereDay(String column, int day) {
        Objects.requireNonNull(column, "column");
        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("day must be between 1 and 31");
        }
        andPart((from, cb) -> cb.equal(datePart(cb, from.get(column), "day"), day));
        return this;
    }

    /**
     * Matches clock time ({@code hour} AND {@code minute} AND {@code second}).
     *
     * @param column temporal attribute name
     * @param time   clock time; must not be {@code null}
     * @return {@code this} for chaining
     */
    public RelatedFilter whereTime(String column, LocalTime time) {
        Objects.requireNonNull(column, "column");
        Objects.requireNonNull(time, "time");
        andPart((from, cb) -> {
            Path<?> path = from.get(column);
            return cb.and(
                    cb.equal(datePart(cb, path, "hour"), time.getHour()),
                    cb.equal(datePart(cb, path, "minute"), time.getMinute()),
                    cb.equal(datePart(cb, path, "second"), time.getSecond()));
        });
        return this;
    }

    /**
     * Equality only when a value is present ({@code null} / blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhere(String column, Object value) {
        return optionalWhereEqual(column, value);
    }

    /**
     * Same as {@link #optionalWhere(String, Object)}.
     *
     * @param column attribute name on the related entity
     * @param value  expected value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereEqual(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : whereEqual(column, normalized);
    }

    /**
     * Inequality only when a value is present ({@code null} / blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  forbidden value; blank strings are treated as absent
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereNotEqual(String column, Object value) {
        Object normalized = normalizeEqualValue(value);
        return normalized == null ? this : whereNotEqual(column, normalized);
    }

    /**
     * {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  substring; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereLike(String column, String value) {
        return Values.isBlank(value) ? this : whereLike(column, value);
    }

    /**
     * Escaped contains {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  free-text substring; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereContains(String column, String value) {
        return Values.isBlank(value) ? this : whereContains(column, value);
    }

    /**
     * Escaped prefix {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  prefix; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereStartsWith(String column, String value) {
        return Values.isBlank(value) ? this : whereStartsWith(column, value);
    }

    /**
     * Escaped suffix {@code LIKE} only when {@code value} has text (blank → no-op).
     *
     * @param column attribute name on the related entity
     * @param value  suffix; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereEndsWith(String column, String value) {
        return Values.isBlank(value) ? this : whereEndsWith(column, value);
    }

    /**
     * Raw {@code LIKE} pattern only when {@code pattern} has text (blank → no-op).
     *
     * @param column  attribute name on the related entity
     * @param pattern raw LIKE pattern; blank → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereLikePattern(String column, String pattern) {
        return Values.isBlank(pattern) ? this : whereLikePattern(column, pattern);
    }

    /**
     * {@code IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name on the related entity
     * @param values allowed values; empty → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : whereIn(column, values);
    }

    /**
     * {@code NOT IN} only when the collection is non-empty ({@code null}/empty → no-op).
     *
     * @param column attribute name on the related entity
     * @param values forbidden values; empty → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereNotIn(String column, Collection<?> values) {
        return Values.isEmpty(values) ? this : whereNotIn(column, values);
    }

    /**
     * Alias for {@link #optionalWhereGreaterThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereGt(String column, Y value) {
        return optionalWhereGreaterThan(column, value);
    }

    /**
     * {@code >} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereGreaterThan(
            String column, Y value) {
        return value == null ? this : whereGreaterThan(column, value);
    }

    /**
     * Alias for {@link #optionalWhereGreaterThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereGte(String column, Y value) {
        return optionalWhereGreaterThanOrEqualTo(column, value);
    }

    /**
     * {@code >=} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive lower bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereGreaterThanOrEqualTo(
            String column, Y value) {
        return value == null ? this : whereGreaterThanOrEqualTo(column, value);
    }

    /**
     * Alias for {@link #optionalWhereLessThan(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereLt(String column, Y value) {
        return optionalWhereLessThan(column, value);
    }

    /**
     * {@code <} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  exclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereLessThan(
            String column, Y value) {
        return value == null ? this : whereLessThan(column, value);
    }

    /**
     * Alias for {@link #optionalWhereLessThanOrEqualTo(String, Comparable)}.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereLte(String column, Y value) {
        return optionalWhereLessThanOrEqualTo(column, value);
    }

    /**
     * {@code <=} only when {@code value} is non-null.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param value  inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereLessThanOrEqualTo(
            String column, Y value) {
        return value == null ? this : whereLessThanOrEqualTo(column, value);
    }

    /**
     * Inclusive range only when at least one bound is present; both {@code null} → no-op.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param from   inclusive lower bound; may be {@code null}
     * @param to     inclusive upper bound; may be {@code null}
     * @return {@code this} for chaining
     * @see #whereBetween(String, Comparable, Comparable)
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereBetween(
            String column, Y from, Y to) {
        if (from == null && to == null) {
            return this;
        }
        return whereBetween(column, from, to);
    }

    /**
     * {@code NOT BETWEEN} only when both bounds are non-null; either {@code null} → no-op.
     *
     * @param <Y>    comparable type
     * @param column attribute name on the related entity
     * @param from   inclusive lower bound; {@code null} → no-op
     * @param to     inclusive upper bound; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public <Y extends Comparable<? super Y>> RelatedFilter optionalWhereNotBetween(
            String column, Y from, Y to) {
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
    public RelatedFilter optionalWhereDate(String column, LocalDate date) {
        return date == null ? this : whereDate(column, date);
    }

    /**
     * Year match only when {@code year} is non-null.
     *
     * @param column temporal attribute name
     * @param year   calendar year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereYear(String column, Integer year) {
        return year == null ? this : whereYear(column, year);
    }

    /**
     * Month match only when {@code month} is non-null.
     *
     * @param column temporal attribute name
     * @param month  month of year; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereMonth(String column, Integer month) {
        return month == null ? this : whereMonth(column, month);
    }

    /**
     * Day match only when {@code day} is non-null.
     *
     * @param column temporal attribute name
     * @param day    day of month; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereDay(String column, Integer day) {
        return day == null ? this : whereDay(column, day);
    }

    /**
     * Time match only when {@code time} is non-null.
     *
     * @param column temporal attribute name
     * @param time   clock time; {@code null} → no-op
     * @return {@code this} for chaining
     */
    public RelatedFilter optionalWhereTime(String column, LocalTime time) {
        return time == null ? this : whereTime(column, time);
    }

    /**
     * Builds the AND of all collected predicates. Empty filter → {@code conjunction}
     * (always true).
     *
     * @param from related {@link From} (typically a correlated {@code Join})
     * @param cb   criteria builder
     * @return combined predicate (never {@code null})
     */
    public Predicate toPredicate(From<?, ?> from, CriteriaBuilder cb) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(cb, "cb");
        return tree == null ? cb.conjunction() : tree.apply(from, cb);
    }

    private RelatedFilter andPart(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> part) {
        Objects.requireNonNull(part, "part");
        if (tree == null) {
            tree = part;
        } else {
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> left = tree;
            tree = (from, cb) -> cb.and(left.apply(from, cb), part.apply(from, cb));
        }
        return this;
    }

    private RelatedFilter orPart(BiFunction<From<?, ?>, CriteriaBuilder, Predicate> part) {
        Objects.requireNonNull(part, "part");
        if (tree == null) {
            tree = part;
        } else {
            BiFunction<From<?, ?>, CriteriaBuilder, Predicate> left = tree;
            tree = (from, cb) -> cb.or(left.apply(from, cb), part.apply(from, cb));
        }
        return this;
    }

    private static Expression<Integer> datePart(CriteriaBuilder cb, Path<?> path, String function) {
        return cb.function(function, Integer.class, path);
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
