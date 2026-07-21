package dev.benjaminor.fluentquery.support;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;

/**
 * Reuses an existing Criteria {@link Join} on the same association when possible, avoiding
 * duplicate joins that inflate result sets.
 */
public final class Joins {

    private Joins() {
    }

    /**
     * Returns an existing join for {@code relation} on {@code from}, or creates a new one.
     *
     * @param from     root or parent from which to join
     * @param relation association attribute name
     * @param joinType join type used only when creating a new join
     * @return existing or newly created join
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Join<?, ?> reuseOrCreate(From<?, ?> from, String relation, JoinType joinType) {
        for (Join<?, ?> existing : from.getJoins()) {
            Attribute<?, ?> attr = existing.getAttribute();
            if (attr != null && relation.equals(attr.getName())) {
                return existing;
            }
        }
        return from.join(relation, joinType);
    }
}
