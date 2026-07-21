package dev.benjaminor.fluentquery.support;

/**
 * Builds case-insensitive {@code LIKE} patterns for {@link dev.benjaminor.fluentquery.FluentQuery}.
 *
 * <p><b>Default ({@link #toPattern(String)}):</b>
 * <ul>
 *   <li>If {@code value} already contains {@code %} or {@code _}, it is treated as a <em>raw</em>
 *       pattern (only trimmed + upper-cased) — you control the wildcards.</li>
 *   <li>Otherwise it becomes a contains match: {@code %VALUE%}.</li>
 * </ul>
 *
 * <pre>{@code
 * LikePatterns.toPattern("ada");    // %ADA%
 * LikePatterns.toPattern("ADA%");   // ADA%   (starts with)
 * LikePatterns.toPattern("%ADA");   // %ADA   (ends with)
 * LikePatterns.toPattern("_DA%");   // _DA%   (raw)
 * }</pre>
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Resolves a user/search string into a LIKE pattern.
     *
     * @param value non-blank search text
     * @return upper-cased pattern, either raw (if wildcards present) or {@code %VALUE%}
     */
    public static String toPattern(String value) {
        String trimmed = value.trim();
        String upper = trimmed.toUpperCase();
        return hasWildcard(trimmed) ? upper : "%" + upper + "%";
    }

    /**
     * {@code true} if {@code value} contains {@code %} or {@code _}.
     *
     * @param value maybe-null string
     * @return whether a wildcard character is present
     */
    public static boolean hasWildcard(String value) {
        return value != null && (value.indexOf('%') >= 0 || value.indexOf('_') >= 0);
    }

    /**
     * Escapes {@code %} and {@code _} so they match literally, then wraps as contains.
     * Use when the input is free text and must not be interpreted as wildcards.
     *
     * @param value free-text substring (should already be non-blank)
     * @return upper-cased escaped contains pattern, e.g. {@code %100\%%}
     */
    public static String containsEscaped(String value) {
        return "%" + escapeWildcards(value.trim()).toUpperCase() + "%";
    }

    /**
     * Escapes wildcards and appends {@code %} (prefix match).
     *
     * @param value free-text prefix
     * @return upper-cased escaped prefix pattern, e.g. {@code ADA%}
     */
    public static String startsWithEscaped(String value) {
        return escapeWildcards(value.trim()).toUpperCase() + "%";
    }

    /**
     * Escapes wildcards and prepends {@code %} (suffix match).
     *
     * @param value free-text suffix
     * @return upper-cased escaped suffix pattern, e.g. {@code %ADA}
     */
    public static String endsWithEscaped(String value) {
        return "%" + escapeWildcards(value.trim()).toUpperCase();
    }

    /**
     * Escape {@code %} → {@code \%}, {@code _} → {@code \_}, {@code \} → {@code \\}.
     *
     * @param value raw text
     * @return escaped text suitable for {@code LIKE … ESCAPE '\'}
     */
    public static String escapeWildcards(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
