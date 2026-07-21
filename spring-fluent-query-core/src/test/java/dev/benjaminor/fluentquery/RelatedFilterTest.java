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
        when(cb.and(any(Predicate[].class))).thenReturn(and);

        assertThat(filter.toPredicate(from, cb)).isSameAs(and);
        verify(cb).equal(path, "Spring");
    }

    @Test
    void whereLike_rejectsBlank() {
        RelatedFilter filter = new RelatedFilter();
        assertThatThrownBy(() -> filter.whereLike("title", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void optionalWhereContains_skipsBlank() {
        RelatedFilter filter = new RelatedFilter().optionalWhereContains("title", " ");
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        @SuppressWarnings("unchecked")
        From<?, ?> from = mock(From.class);

        assertThat(filter.toPredicate(from, cb)).isSameAs(conjunction);
        verify(from, never()).get(any(String.class));
    }
}
