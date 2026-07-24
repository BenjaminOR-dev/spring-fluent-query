package dev.benjaminor.fluentquery;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelatedFilterTest {

    @Test
    void optionalWhereGt_skipsNull() {
        RelatedFilter filter = new RelatedFilter().optionalWhereGt("pages", null);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);

        assertThat(filter.toPredicate(from, cb)).isSameAs(conjunction);
        verify(from, never()).get(any(String.class));
    }

    @Test
    void optionalWhereBetween_skipsWhenBothBoundsNull() {
        RelatedFilter filter = new RelatedFilter().optionalWhereBetween("pages", null, null);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);

        assertThat(filter.toPredicate(from, cb)).isSameAs(conjunction);
        verify(from, never()).get(any(String.class));
    }

    @Test
    void optionalWhereEqual_appliesWhenPresent() {
        RelatedFilter filter = new RelatedFilter().optionalWhereEqual("title", "Spring");
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        Predicate equal = mock(Predicate.class);
        Predicate and = mock(Predicate.class);
        when(from.get("title")).thenReturn(path);
        when(cb.equal(path, "Spring")).thenReturn(equal);

        assertThat(filter.toPredicate(from, cb)).isSameAs(equal);
        verify(cb).equal(path, "Spring");
        verify(cb, never()).and(any(Predicate[].class));
    }

    @Test
    void whereLike_rejectsBlank() {
        RelatedFilter filter = new RelatedFilter();
        assertThatThrownBy(() -> filter.whereLike("title", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orWhere_composesWithOr() {
        RelatedFilter filter = new RelatedFilter()
                .whereEqual("title", "A")
                .orWhere("title", "B");
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        Predicate eqA = mock(Predicate.class);
        Predicate eqB = mock(Predicate.class);
        Predicate or = mock(Predicate.class);
        when(from.get("title")).thenReturn(path);
        when(cb.equal(path, "A")).thenReturn(eqA);
        when(cb.equal(path, "B")).thenReturn(eqB);
        when(cb.or(eqA, eqB)).thenReturn(or);

        assertThat(filter.toPredicate(from, cb)).isSameAs(or);
    }

    @Test
    void orWhere_emptyGroup_isIgnored_notAlwaysTrue() {
        RelatedFilter filter = new RelatedFilter()
                .whereEqual("a", 1)
                .orWhere(g -> {
                });
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        Predicate eq = mock(Predicate.class);
        when(from.get("a")).thenReturn(path);
        when(cb.equal(path, 1)).thenReturn(eq);

        assertThat(filter.toPredicate(from, cb)).isSameAs(eq);
        verify(cb, never()).or(any(Predicate.class), any(Predicate.class));
        verify(cb, never()).conjunction();
    }

    @Test
    void where_emptyGroup_isIgnored() {
        RelatedFilter filter = new RelatedFilter()
                .whereEqual("a", 1)
                .where(g -> {
                });
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        Predicate eq = mock(Predicate.class);
        when(from.get("a")).thenReturn(path);
        when(cb.equal(path, 1)).thenReturn(eq);

        assertThat(filter.toPredicate(from, cb)).isSameAs(eq);
        verify(cb, never()).and(any(Predicate.class), any(Predicate.class));
        verify(cb, never()).conjunction();
    }

    @Test
    void whereColumn_rejectsBadOperator() {
        assertThatThrownBy(() -> new RelatedFilter().whereColumn("a", "~~", "b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
    }
}
