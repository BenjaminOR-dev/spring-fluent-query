package dev.benjaminor.fluentquery.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternsTest {

    @Test
    void toPattern_wrapsWhenNoWildcard() {
        assertThat(LikePatterns.toPattern("ada")).isEqualTo("%ADA%");
        assertThat(LikePatterns.toPattern("  Ada  ")).isEqualTo("%ADA%");
    }

    @Test
    void toPattern_keepsRawWhenWildcardPresent() {
        assertThat(LikePatterns.toPattern("ADA%")).isEqualTo("ADA%");
        assertThat(LikePatterns.toPattern("%ada")).isEqualTo("%ADA");
        assertThat(LikePatterns.toPattern("_x%")).isEqualTo("_X%");
        assertThat(LikePatterns.toPattern("A_B")).isEqualTo("A_B");
    }

    @Test
    void escaped_helpersTreatWildcardsAsLiteral() {
        assertThat(LikePatterns.containsEscaped("100%")).isEqualTo("%100\\%%");
        assertThat(LikePatterns.startsWithEscaped("A_B")).isEqualTo("A\\_B%");
        assertThat(LikePatterns.endsWithEscaped("x%y")).isEqualTo("%X\\%Y");
        assertThat(LikePatterns.escapeWildcards("a\\b%c_d")).isEqualTo("a\\\\b\\%c\\_d");
    }

    @Test
    void toPattern_usesLocaleRoot() {
        // Turkish locale must not affect i/I mapping for library patterns
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(LikePatterns.toPattern("title")).isEqualTo("%TITLE%");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }
}
