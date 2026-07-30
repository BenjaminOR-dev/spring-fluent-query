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

    @Test
    void fetchCollection_withLimit_isRejected() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetchCollection("items")
                        .limit(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void fetch_withColumnShorthand_isRejected() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).fetch("status:id,name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("select");
    }

    @Test
    void group_rejectsNestedFetch() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .where(q -> q.fetch("profile").where("active", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("groups");
    }

    @Test
    void with_requiresConstraintsConsumer() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).fetch("profile", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fetch_FetchRel_batchMixesPlainAndConstrained() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> q = FluentQuery.of(executor).fetch(
                FetchRel.of("rel1.rel2", f -> f.where("active", true)),
                FetchRel.of("rel3"),
                FetchRel.of("rel4", f -> f.whereNotNull("code")));
        assertThat(q).isNotNull();
    }

    @Test
    void fetch_FetchRel_rejectsEmpty() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor).fetch(new FetchRel[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fetch_FetchRel_rejectsColon() {
        assertThatThrownBy(() -> FetchRel.of("status:id,name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(":");
    }

    @Test
    void fetch_FetchRel_rejectsBlankPath() {
        assertThatThrownBy(() -> FetchRel.of("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> FetchRel.of("\u00A0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void first_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .first(Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first");
    }

    @Test
    void one_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .one(Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one");
    }

    @Test
    void latest_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .latest("createdAt", Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first");
    }

    @Test
    void firstOrFail_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .firstOrFail(Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first");
    }

    @Test
    void slice_and_paginate_withClass_rejectWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .slice(PageRequest.of(0, 10), Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slice");

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .paginate(0, 10, Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page");
    }

    @Test
    void oldest_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .oldest("createdAt", Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("first");
    }

    @Test
    void get_withClass_rejectsWhenFetchCollectionConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetchCollection("books")
                        .get(Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("get");
    }

    @Test
    void page_withClass_rejectsWhenFetchConfigured() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("profile")
                        .page(PageRequest.of(0, 10), Object.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("page");
    }

    @Test
    void fetch_and_fetchCollection_samePath_rejected() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        assertThatThrownBy(() -> FluentQuery.of(executor)
                        .fetch("items")
                        .fetchCollection("items"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both");
    }

    @Test
    void plainFetch_clearsPriorOnConstraint() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        FluentQuery<Object> q = FluentQuery.of(executor)
                .fetch("profile", f -> f.where("active", true))
                .fetch("profile");
        assertThat(q).isNotNull();
    }
}
