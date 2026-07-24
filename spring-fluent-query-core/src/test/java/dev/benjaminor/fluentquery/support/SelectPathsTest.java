package dev.benjaminor.fluentquery.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelectPathsTest {

    @Test
    void expand_plainPath() {
        assertThat(SelectPaths.expand("id")).containsExactly("id");
        assertThat(SelectPaths.expand("status.description")).containsExactly("status.description");
    }

    @Test
    void expand_shorthand() {
        assertThat(SelectPaths.expand("status:id,description"))
                .containsExactly("status.id", "status.description");
        assertThat(SelectPaths.expand("company.address:id,city"))
                .containsExactly("company.address.id", "company.address.city");
    }

    @Test
    void expand_rejectsMalformed() {
        assertThatThrownBy(() -> SelectPaths.expand(":id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectPaths.expand("status:"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SelectPaths.expand("status:id,"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expandAll_dedupes() {
        assertThat(SelectPaths.expandAll("id", "status:id", "id"))
                .containsExactly("id", "status.id");
    }
}
