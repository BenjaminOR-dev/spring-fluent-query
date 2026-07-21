package dev.benjaminor.fluentquery;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FluentQueryTest {

    @AfterEach
    void resetDefaults() {
        FluentQueryDefaults.reset();
    }

    @Test
    void defaults_arePortable() {
        assertThat(FluentQueryDefaults.likeMode()).isEqualTo(LikeMode.PORTABLE);
    }

    @Test
    void fetchCollection_withPage_isRejected() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetchCollection("items")
                        .page(PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fetchCollection");
    }

    @Test
    void fetchCollection_withSlice_isRejected() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetchCollection("items")
                        .slice(PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void paginate_rejectsInvalidArgs() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);
        FluentQuery<Object> q = FluentQuery.of(executor);

        assertThatThrownBy(() -> q.paginate(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> q.paginate(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whereLike_rejectsNull() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).whereLike("name", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void whereLike_rejectsBlank() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).whereLike("name", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void optionalWhereLike_acceptsBlankWithoutTerminal() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> q = FluentQuery.of(executor).optionalWhereLike("name", "  ");
        assertThat(q).isNotNull();
    }

    @Test
    void optionalWhereContains_acceptsBlankWithoutTerminal() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThat(FluentQuery.of(executor).optionalWhereContains("name", "")).isNotNull();
    }

    @Test
    void select_rejectsEmptyAndBlank() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).select(new String[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FluentQuery.of(executor).select("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FluentQuery.of(executor).select(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whereColumn_rejectsInvalidOperator() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).whereColumn("a", "~~", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
    }

    @Test
    void whereMonth_rejectsOutOfRange() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).whereMonth("createdAt", 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("month");
    }

    @Test
    void unless_isInverseOfWhen() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> q = FluentQuery.of(executor).unless(true, query -> query.where("x", 1));
        assertThat(q).isNotNull();
    }

    @Test
    void optionalWhereGt_skipsNull() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> base = FluentQuery.of(executor);
        assertThat(base.optionalWhereGt("age", (Integer) null)).isSameAs(base);
    }

    @Test
    void optionalWhereBetween_skipsWhenBothBoundsNull() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> base = FluentQuery.of(executor);
        assertThat(base.optionalWhereBetween("age", null, null)).isSameAs(base);
    }

    @Test
    void optionalOrWhereLike_skipsBlank() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> base = FluentQuery.of(executor);
        assertThat(base.optionalOrWhereLike("name", "  ")).isSameAs(base);
    }
}
