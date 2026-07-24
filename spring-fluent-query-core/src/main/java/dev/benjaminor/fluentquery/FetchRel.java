package dev.benjaminor.fluentquery;

import dev.benjaminor.fluentquery.support.Values;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One eager-load entry for {@link FluentQuery#fetch(FetchRel...)} /
 * {@link FluentQuery#fetchCollection(FetchRel...)} — Java equivalent of Eloquent:
 *
 * <pre>{@code
 * ->with([
 *     'rel1.rel2' => fn ($q) => $q->where(...),
 *     'rel3',
 *     'rel4' => fn ($q) => $q->where(...),
 * ])
 * }</pre>
 *
 * <pre>{@code
 * query().fetch(
 *     FetchRel.of("rel1.rel2", f -> f.where("active", true)),
 *     FetchRel.of("rel3"),
 *     FetchRel.of("rel4", f -> f.whereNotNull("code"))
 * );
 * }</pre>
 *
 * <p>Column lists ({@code "rel:id,name"}) are <b>not</b> allowed — JOIN FETCH loads the full
 * association; use root {@code select(...).getAs(...)} for lean projections.
 *
 * @param path        association path (may be dotted; constraints apply to the leaf)
 * @param constraints related {@code ON} predicates; {@code null} = plain fetch
 */
public record FetchRel(String path, Consumer<RelatedFilter> constraints) {

    /**
     * Plain eager load (no {@code ON} constraints).
     *
     * @param path association path
     * @return fetch spec
     */
    public static FetchRel of(String path) {
        return new FetchRel(path, null);
    }

    /**
     * Constrained eager load ({@code ON} on the leaf join).
     *
     * @param path        association path
     * @param constraints related predicates; must not be {@code null}
     * @return fetch spec
     */
    public static FetchRel of(String path, Consumer<RelatedFilter> constraints) {
        Objects.requireNonNull(constraints, "constraints");
        return new FetchRel(path, constraints);
    }

    public FetchRel {
        path = Values.requireText(path, "path must not be blank").trim();
        if (path.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "FetchRel path must not contain ':' (got '" + path + "'). "
                            + "JOIN FETCH loads the full association; use select(\"assoc:col1,col2\") "
                            + "with getAs/firstAs for column projections.");
        }
    }

    /** {@code true} when this entry has {@code ON} constraints. */
    public boolean constrained() {
        return constraints != null;
    }
}
