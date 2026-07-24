package dev.benjaminor.fluentquery.example.it;

import dev.benjaminor.fluentquery.lifecycle.AbstractEntityLifecycleListener;
import dev.benjaminor.fluentquery.lifecycle.EnableFluentQueryLifecycle;
import dev.benjaminor.fluentquery.lifecycle.FluentQueryLifecycleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 IT: Eloquent-style entity lifecycle hooks via {@link EnableFluentQueryLifecycle}.
 */
@SpringBootTest(classes = EntityLifecycleHooksIT.Config.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:fluentquery-lifecycle-it;DB_CLOSE_DELAY=-1;MODE=LEGACY",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.fluent-query.lifecycle.enabled=true"
})
@Transactional
class EntityLifecycleHooksIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFluentQueryLifecycle(basePackageClasses = AuthorRepository.class)
    @Import(RecordingAuthorLifecycle.class)
    static class Config {
    }

    @Autowired
    AuthorRepository authors;

    @Autowired
    BookRepository books;

    @Autowired
    RecordingAuthorLifecycle lifecycle;

    @BeforeEach
    void clean() {
        books.deleteAll();
        authors.deleteAll();
        lifecycle.clear();
    }

    @Test
    void saveNew_orderCreatingCreatedSavingSaved() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        assertThat(lifecycle.events).containsExactly(
                "onSaving", "onCreating", "onCreated", "onSaved");
    }

    @Test
    void saveExisting_orderUpdating() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);
        lifecycle.clear();

        ada.setName("Ada Lovelace");
        authors.save(ada);

        assertThat(lifecycle.events).containsExactly(
                "onSaving", "onUpdating", "onUpdated", "onSaved");
    }

    @Test
    void delete_orderDeletingDeleted() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);
        lifecycle.clear();

        authors.delete(ada);

        assertThat(lifecycle.events).containsExactly("onDeleting", "onDeleted");
    }

    @Test
    void saveAll_threeItems_threeCreated() {
        Author a = named("A");
        Author b = named("B");
        Author c = named("C");
        authors.saveAll(List.of(a, b, c));

        long created = lifecycle.events.stream().filter("onCreated"::equals).count();
        assertThat(created).isEqualTo(3);
    }

    @Test
    void queryDelete_firesPerEntity() {
        authors.save(named("Temp"));
        authors.save(named("Temp"));
        authors.save(named("Keep"));
        lifecycle.clear();

        long removed = authors.query().where("name", "Temp").delete();

        assertThat(removed).isEqualTo(2);
        long deleting = lifecycle.events.stream().filter("onDeleting"::equals).count();
        long deleted = lifecycle.events.stream().filter("onDeleted"::equals).count();
        assertThat(deleting).isEqualTo(2);
        assertThat(deleted).isEqualTo(2);
    }

    @Test
    void deleteAllInBatch_skipsHooks() {
        authors.save(named("X"));
        authors.save(named("Y"));
        lifecycle.clear();

        authors.deleteAllInBatch();

        assertThat(lifecycle.events).isEmpty();
        assertThat(authors.count()).isZero();
    }

    @Test
    void onCreating_throws_abortsSaveInTransaction() {
        lifecycle.failOnCreating = true;
        Author ada = named("Ada");

        assertThatThrownBy(() -> authors.save(ada))
                .isInstanceOf(FluentQueryLifecycleException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("blocked-creating");

        assertThat(lifecycle.events).containsExactly("onSaving", "onCreating");
        assertThat(authors.count()).isZero();
    }

    private static Author named(String name) {
        Author a = new Author();
        a.setName(name);
        return a;
    }

    @Component
    static class RecordingAuthorLifecycle extends AbstractEntityLifecycleListener<Author> {

        final List<String> events = new ArrayList<>();
        boolean failOnCreating;

        void clear() {
            events.clear();
            failOnCreating = false;
        }

        @Override
        public void onSaving(Author entity) {
            events.add("onSaving");
        }

        @Override
        public void onCreating(Author entity) {
            events.add("onCreating");
            if (failOnCreating) {
                throw new IllegalStateException("blocked-creating");
            }
        }

        @Override
        public void onCreated(Author entity) {
            events.add("onCreated");
        }

        @Override
        public void onUpdating(Author entity) {
            events.add("onUpdating");
        }

        @Override
        public void onUpdated(Author entity) {
            events.add("onUpdated");
        }

        @Override
        public void onSaved(Author entity) {
            events.add("onSaved");
        }

        @Override
        public void onDeleting(Author entity) {
            events.add("onDeleting");
        }

        @Override
        public void onDeleted(Author entity) {
            events.add("onDeleted");
        }
    }
}
