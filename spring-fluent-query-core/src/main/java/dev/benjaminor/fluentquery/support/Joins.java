package dev.benjaminor.fluentquery.support;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;

/**
 * Reuses an existing Criteria {@link Join} on the same association when possible, avoiding
 * duplicate joins that inflate result sets. Also walks dotted association paths
 * ({@code "company.address"}).
 */
public final class Joins {

    private Joins() {
    }

    /**
     * Returns an existing join for {@code relation} on {@code from}, or creates a new one.
     *
     * @param from     root or parent from which to join
     * @param relation association attribute name (single segment)
     * @param joinType join type used only when creating a new join
     * @return existing or newly created join
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Join<?, ?> reuseOrCreate(From<?, ?> from, String relation, JoinType joinType) {
        for (Join<?, ?> existing : from.getJoins()) {
            Attribute<?, ?> attr = existing.getAttribute();
            if (attr != null
                    && relation.equals(attr.getName())
                    && existing.getJoinType() == joinType) {
                return existing;
            }
        }
        return from.join(relation, joinType);
    }

    /**
     * INNER/LEFT JOIN for a possibly dotted association path, reusing intermediate joins.
     *
     * <p>Examples: {@code "company"}, {@code "company.address"}.
     *
     * @param from     root or correlated root
     * @param path     association path; must not be blank
     * @param joinType join type for each segment
     * @return the outermost join
     * @throws IllegalArgumentException if {@code path} is blank or has a blank segment
     */
    public static Join<?, ?> joinPath(From<?, ?> from, String path, JoinType joinType) {
        if (!Values.hasText(path)) {
            throw new IllegalArgumentException("join path must not be blank");
        }
        String[] segments = path.trim().split("\\.");
        From<?, ?> current = from;
        Join<?, ?> last = null;
        for (String segment : segments) {
            if (!Values.hasText(segment)) {
                throw new IllegalArgumentException("join path contains a blank segment: '" + path + "'");
            }
            last = reuseOrCreate(current, segment.trim(), joinType);
            current = last;
        }
        return last;
    }

    /** {@code true} when {@code path} has more than one association segment. */
    public static boolean isNestedPath(String path) {
        return Values.hasText(path) && path.indexOf('.') >= 0;
    }
}
