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
    void hasWildcard() {
        assertThat(LikePatterns.hasWildcard("plain")).isFalse();
        assertThat(LikePatterns.hasWildcard("a%")).isTrue();
        assertThat(LikePatterns.hasWildcard("a_b")).isTrue();
        assertThat(LikePatterns.hasWildcard(null)).isFalse();
    }
}
