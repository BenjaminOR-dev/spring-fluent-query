package dev.benjaminor.fluentquery.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Expands compact select tokens into property paths.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "id"} → {@code id}</li>
 *   <li>{@code "status:id,description"} → {@code status.id}, {@code status.description}</li>
 *   <li>{@code "company.address:id,city"} →
 *       {@code company.address.id}, {@code company.address.city}</li>
 * </ul>
 *
 * <p>This is for {@code select}/{@code get(Class)} projections — not for {@code fetch}
 * (JPA cannot JOIN FETCH a partial entity state safely).
 */
public final class SelectPaths {

    private SelectPaths() {
    }

    /**
     * Expands one select token (plain path or {@code association:col1,col2}).
     *
     * @param token non-blank token
     * @return one or more property paths
     * @throws IllegalArgumentException if the token is malformed
     */
    public static List<String> expand(String token) {
        if (!Values.hasText(token)) {
            throw new IllegalArgumentException("select path must not be blank");
        }
        String trimmed = token.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return List.of(trimmed);
        }
        String association = trimmed.substring(0, colon).trim();
        String columnsPart = trimmed.substring(colon + 1).trim();
        if (!Values.hasText(association)) {
            throw new IllegalArgumentException(
                    "select shorthand requires an association before ':': '" + token + "'");
        }
        if (!Values.hasText(columnsPart)) {
            throw new IllegalArgumentException(
                    "select shorthand requires at least one column after ':': '" + token + "'");
        }
        String[] columns = columnsPart.split(",", -1);
        List<String> paths = new ArrayList<>(columns.length);
        for (String column : columns) {
            if (!Values.hasText(column)) {
                throw new IllegalArgumentException(
                        "select shorthand has a blank column in: '" + token + "'");
            }
            paths.add(association + "." + column.trim());
        }
        return paths;
    }

    /**
     * Expands many tokens; preserves order and deduplicates.
     *
     * @param tokens select tokens
     * @return expanded property paths
     */
    public static List<String> expandAll(String... tokens) {
        List<String> out = new ArrayList<>();
        if (tokens == null) {
            return out;
        }
        for (String token : tokens) {
            for (String path : expand(token)) {
                if (!out.contains(path)) {
                    out.add(path);
                }
            }
        }
        return out;
    }
}
