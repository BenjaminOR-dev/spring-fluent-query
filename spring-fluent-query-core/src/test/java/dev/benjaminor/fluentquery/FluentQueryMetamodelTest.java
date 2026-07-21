package dev.benjaminor.fluentquery;

import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies metamodel overloads delegate via {@code Attribute#getName()} without NPE.
 */
class FluentQueryMetamodelTest {

    @Test
    void metamodelOverloads_delegateViaAttributeName() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        @SuppressWarnings("unchecked")
        SingularAttribute<Object, String> email = mock(SingularAttribute.class);
        when(email.getName()).thenReturn("email");

        @SuppressWarnings("unchecked")
        SingularAttribute<Object, String> createdAt = mock(SingularAttribute.class);
        when(createdAt.getName()).thenReturn("createdAt");

        FluentQuery<Object> q = FluentQuery.of(executor)
                .where(email, "a@b.com")
                .whereEqual(email, "a@b.com")
                .whereNotEqual(email, "x")
                .whereLike(email, "a@")
                .whereIn(email, List.of("a@b.com"))
                .whereNotIn(email, List.of("x"))
                .whereNull(email)
                .whereNotNull(email)
                .optionalWhere(email, "a@b.com")
                .optionalWhereLike(email, "a@")
                .optionalWhereIn(email, List.of("a@b.com"))
                .optionalWhereNotEqual(email, "x")
                .orWhere(email, "other")
                .optionalOrWhere(email, "other")
                .orderByAsc(email)
                .orderByDesc(createdAt)
                .fetch(email);

        assertThat(q).isNotNull();
        assertThat(q.toSort().getOrderFor("email")).isNotNull();
        assertThat(q.toSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);

        assertThatCode(() -> FluentQuery.of(executor).optionalWhereLike(email, "  "))
                .doesNotThrowAnyException();
    }

    @Test
    void whereHas_metamodel_delegatesWithoutNpe() {
        @SuppressWarnings("unchecked")
        JpaSpecificationExecutor<Object> executor = mock(JpaSpecificationExecutor.class);

        @SuppressWarnings("unchecked")
        SingularAttribute<Object, Object> profile = mock(SingularAttribute.class);
        when(profile.getName()).thenReturn("profile");

        assertThatCode(() -> FluentQuery.of(executor)
                        .whereHas(profile)
                        .whereDoesntHave(profile)
                        .whereHas(profile, f -> f.where("active", true))
                        .whereDoesntHave(profile, f -> f.whereNull("deletedAt")))
                .doesNotThrowAnyException();
    }
}
