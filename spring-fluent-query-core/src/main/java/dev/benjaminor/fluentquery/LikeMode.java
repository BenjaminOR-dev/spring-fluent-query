package dev.benjaminor.fluentquery;

/**
 * Strategy for case-insensitive {@code LIKE} predicates.
 *
 * <p>Default is {@link #PORTABLE} (works on H2, PostgreSQL, MySQL, SQL Server, Oracle, … via
 * standard JPA {@code UPPER} + {@code LIKE}). Use {@link #ORACLE_UNACCENT} only when you need
 * Oracle {@code CONVERT(..., 'US7ASCII')} accent folding.
 *
 * @see FluentQueryDefaults
 * @see PropertyFilters#hasPropertyLike(String, String)
 */
public enum LikeMode {

    /**
     * {@code UPPER(column) LIKE %VALUE%} — Criteria portable across JPA providers / databases.
     */
    PORTABLE,

    /**
     * Oracle-oriented: {@code UPPER(CONVERT(column, 'US7ASCII')) LIKE %VALUE%}.
     * Not portable to H2 / PostgreSQL / MySQL.
     */
    ORACLE_UNACCENT
}
