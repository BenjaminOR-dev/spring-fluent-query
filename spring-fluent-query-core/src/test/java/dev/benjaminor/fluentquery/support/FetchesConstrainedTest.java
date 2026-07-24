package dev.benjaminor.fluentquery.support;

import dev.benjaminor.fluentquery.RelatedFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class FetchesConstrainedTest {

    @Test
    void applyOn_delegatesToJoinOn() {
        Fetch<?, ?> fetch = mock(Fetch.class, withSettings().extraInterfaces(Join.class));
        @SuppressWarnings("unchecked")
        Join<Object, Object> join = (Join<Object, Object>) fetch;
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Path<Object> path = mock(Path.class);
        Predicate equal = mock(Predicate.class);

        when(join.get("active")).thenReturn(path);
        when(cb.equal(path, true)).thenReturn(equal);

        RelatedFilter filter = new RelatedFilter().whereEqual("active", true);
        Fetches.applyOn(fetch, cb, filter);

        verify(join).on(equal);
    }

    @Test
    void applyOn_rejectsNonJoinFetch() {
        Fetch<?, ?> fetch = mock(Fetch.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        RelatedFilter filter = new RelatedFilter().whereEqual("active", true);

        assertThatThrownBy(() -> Fetches.applyOn(fetch, cb, filter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Join");
    }

    @Test
    void fetchPathConstrained_skipsOnWhenFilterEmpty() {
        @SuppressWarnings("unchecked")
        FetchParent<Object, Object> root = mock(FetchParent.class);
        @SuppressWarnings("unchecked")
        Fetch<Object, Object> created = mock(Fetch.class);
        when(root.getFetches()).thenReturn(Set.of());
        when(root.fetch("profile", JoinType.LEFT)).thenReturn(created);

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Fetches.fetchPathConstrained(root, cb, "profile", JoinType.LEFT, f -> {
        });

        verify(root).fetch("profile", JoinType.LEFT);
        verify(cb, never()).equal(any(), any());
    }
}
