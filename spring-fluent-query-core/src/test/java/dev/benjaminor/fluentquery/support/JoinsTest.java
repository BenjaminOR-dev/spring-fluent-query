package dev.benjaminor.fluentquery.support;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
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

class JoinsTest {

    @Test
    void isNestedPath() {
        assertThat(Joins.isNestedPath(null)).isFalse();
        assertThat(Joins.isNestedPath("")).isFalse();
        assertThat(Joins.isNestedPath("company")).isFalse();
        assertThat(Joins.isNestedPath("company.address")).isTrue();
    }

    @Test
    void joinPath_blank_throws() {
        @SuppressWarnings("unchecked")
        From<Object, Object> from = mock(From.class);
        assertThatThrownBy(() -> Joins.joinPath(from, "  ", JoinType.INNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void joinPath_nested_reusesIntermediateJoin() {
        @SuppressWarnings("unchecked")
        From<Object, Object> root = mock(From.class);
        @SuppressWarnings("unchecked")
        Join<Object, Object> companyJoin = mock(Join.class);
        @SuppressWarnings("unchecked")
        Join<Object, Object> addressJoin = mock(Join.class);
        @SuppressWarnings("unchecked")
        Attribute<Object, Object> companyAttr = mock(Attribute.class);

        when(companyAttr.getName()).thenReturn("company");
        org.mockito.Mockito.doReturn(companyAttr).when(companyJoin).getAttribute();
        when(companyJoin.getJoinType()).thenReturn(JoinType.INNER);

        Set<Join<?, ?>> rootJoins = new LinkedHashSet<>();
        Set<Join<?, ?>> companyJoins = new LinkedHashSet<>();

        when(root.getJoins()).thenAnswer(inv -> Set.copyOf(rootJoins));
        when(companyJoin.getJoins()).thenAnswer(inv -> Set.copyOf(companyJoins));

        when(root.join(eq("company"), eq(JoinType.INNER))).thenAnswer(inv -> {
            rootJoins.add(companyJoin);
            return companyJoin;
        });
        when(companyJoin.join(eq("address"), eq(JoinType.INNER))).thenAnswer(inv -> {
            companyJoins.add(addressJoin);
            return addressJoin;
        });
        when(companyJoin.join(eq("contact"), eq(JoinType.INNER))).thenReturn(mock(Join.class));

        Joins.joinPath(root, "company.address", JoinType.INNER);
        Joins.joinPath(root, "company.contact", JoinType.INNER);

        verify(root, times(1)).join("company", JoinType.INNER);
        verify(companyJoin).join("address", JoinType.INNER);
        verify(companyJoin).join("contact", JoinType.INNER);
    }
}
