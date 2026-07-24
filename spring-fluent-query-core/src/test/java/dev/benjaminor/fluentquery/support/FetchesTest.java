package dev.benjaminor.fluentquery.support;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.FetchParent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.metamodel.Attribute;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchesTest {

    @Test
    void fetchPath_blank_returnsNull() {
        @SuppressWarnings("unchecked")
        FetchParent<Object, Object> root = mock(FetchParent.class);
        assertThat(Fetches.fetchPath(root, "  ", JoinType.LEFT)).isNull();
        assertThat(Fetches.fetchPath(root, null, JoinType.LEFT)).isNull();
    }

    @Test
    void fetchPath_nested_reusesIntermediateFetch() {
        @SuppressWarnings("unchecked")
        FetchParent<Object, Object> root = mock(FetchParent.class);
        @SuppressWarnings("unchecked")
        Fetch<Object, Object> companyFetch = mock(Fetch.class);
        @SuppressWarnings("unchecked")
        Fetch<Object, Object> addressFetch = mock(Fetch.class);
        @SuppressWarnings("unchecked")
        Attribute<Object, Object> companyAttr = mock(Attribute.class);

        when(companyAttr.getName()).thenReturn("company");
        org.mockito.Mockito.doReturn(companyAttr).when(companyFetch).getAttribute();
        when(companyFetch.getJoinType()).thenReturn(JoinType.LEFT);

        Set<Fetch<?, ?>> rootFetches = new LinkedHashSet<>();
        Set<Fetch<?, ?>> companyFetches = new LinkedHashSet<>();

        when(root.getFetches()).thenAnswer(inv -> Set.copyOf(rootFetches));
        when(companyFetch.getFetches()).thenAnswer(inv -> Set.copyOf(companyFetches));

        when(root.fetch(eq("company"), eq(JoinType.LEFT))).thenAnswer(inv -> {
            rootFetches.add(companyFetch);
            return companyFetch;
        });
        when(companyFetch.fetch(eq("address"), eq(JoinType.LEFT))).thenAnswer(inv -> {
            companyFetches.add(addressFetch);
            return addressFetch;
        });
        when(companyFetch.fetch(eq("contact"), eq(JoinType.LEFT))).thenReturn(mock(Fetch.class));

        Fetches.fetchPath(root, "company.address", JoinType.LEFT);
        Fetches.fetchPath(root, "company.contact", JoinType.LEFT);

        verify(root, times(1)).fetch("company", JoinType.LEFT);
        verify(companyFetch).fetch("address", JoinType.LEFT);
        verify(companyFetch).fetch("contact", JoinType.LEFT);
    }

    @Test
    void fetchPath_blankSegment_throws() {
        @SuppressWarnings("unchecked")
        FetchParent<Object, Object> root = mock(FetchParent.class);
        assertThatThrownBy(() -> Fetches.fetchPath(root, "a..b", JoinType.LEFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank segment");
    }
}
