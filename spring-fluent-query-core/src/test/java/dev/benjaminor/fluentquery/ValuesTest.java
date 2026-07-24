package dev.benjaminor.fluentquery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuesTest {

    @Test
    void isBlank_and_trimToNull() {
        assertThat(dev.benjaminor.fluentquery.support.Values.isBlank(null)).isTrue();
        assertThat(dev.benjaminor.fluentquery.support.Values.isBlank("  ")).isTrue();
        assertThat(dev.benjaminor.fluentquery.support.Values.hasText("a")).isTrue();
        assertThat(dev.benjaminor.fluentquery.support.Values.trimToNull("  x  ")).isEqualTo("x");
        assertThat(dev.benjaminor.fluentquery.support.Values.trimToNull("   ")).isNull();
        assertThat(dev.benjaminor.fluentquery.support.Values.isBlank("\u00A0")).isTrue();
        assertThat(dev.benjaminor.fluentquery.support.Values.trimToNull("\u00A0")).isNull();
    }

    @Test
    void requireText_rejectsBlank() {
        assertThatThrownBy(() -> dev.benjaminor.fluentquery.support.Values.requireText("  ", "blank"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("blank");
        assertThat(dev.benjaminor.fluentquery.support.Values.requireText("ok", "blank")).isEqualTo("ok");
    }
}
