package dev.benjaminor.fluentquery.support;

import dev.benjaminor.fluentquery.FluentQueryDefaults;
import dev.benjaminor.fluentquery.LikeMode;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;

/**
 * Builds the left-hand expression used by case-insensitive {@code LIKE} predicates.
 * Respects {@link FluentQueryDefaults#likeMode()}.
 */
public final class LikeExpressions {

    private LikeExpressions() {
    }

    /**
     * Column expression for {@code LIKE}: {@code UPPER(col)} in portable mode, or
     * {@code UPPER(CONVERT(col, 'US7ASCII'))} when {@link LikeMode#ORACLE_UNACCENT} is active.
     *
     * @param from   root or join
     * @param cb     criteria builder
     * @param column attribute name
     * @return string expression ready for {@link CriteriaBuilder#like}
     */
    public static Expression<String> of(From<?, ?> from, CriteriaBuilder cb, String column) {
        if (FluentQueryDefaults.likeMode() == LikeMode.ORACLE_UNACCENT) {
            Expression<String> converted = cb.function(
                    "CONVERT",
                    String.class,
                    from.get(column),
                    cb.literal("US7ASCII"));
            return cb.upper(converted);
        }
        return cb.upper(from.get(column));
    }
}
