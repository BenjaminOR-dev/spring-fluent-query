package dev.benjaminor.fluentquery.example.it;

import dev.benjaminor.fluentquery.FluentQueryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H2 {@link DataJpaTest} coverage for FluentQuery relation filters, optionals, delete, and fetch rules.
 * Nested {@link Config} keeps the example {@code CommandLineRunner} out of the slice.
 */
@DataJpaTest
class FluentQueryDataJpaIT {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Author.class)
    @EnableJpaRepositories(basePackageClasses = AuthorRepository.class)
    static class Config {
    }

    @Autowired
    AuthorRepository authors;

    @Autowired
    BookRepository books;

    @BeforeEach
    void clean() {
        books.deleteAll();
        authors.deleteAll();
    }

    @Test
    void saveAndFirstByName() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        assertThat(authors.query().where("name", "Ada").first())
                .isPresent()
                .get()
                .extracting(Author::getName)
                .isEqualTo("Ada");
    }

    @Test
    void optionalWhereLike_skipsBlank() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        Author grace = new Author();
        grace.setName("Grace");
        authors.save(grace);

        assertThat(authors.query().optionalWhereLike("name", "  ").get())
                .hasSize(2);
    }

    @Test
    void whereLike_acceptsWildcardsAsRawPattern() {
        Author ada = new Author();
        ada.setName("Ada Lovelace");
        authors.save(ada);

        Author grace = new Author();
        grace.setName("Grace Hopper");
        authors.save(grace);

        assertThat(authors.query().whereLike("name", "Ada%").get())
                .extracting(Author::getName)
                .containsExactly("Ada Lovelace");

        assertThat(authors.query().whereStartsWith("name", "Grace").get())
                .extracting(Author::getName)
                .containsExactly("Grace Hopper");

        Author pct = new Author();
        pct.setName("100% Club");
        authors.save(pct);

        assertThat(authors.query().whereContains("name", "100%").get())
                .extracting(Author::getName)
                .containsExactly("100% Club");
    }

    @Test
    void whereHas_and_whereDoesntHave() {
        Author withBooks = new Author();
        withBooks.setName("WithBooks");
        Book book = new Book();
        book.setTitle("Spring");
        book.setPages(200);
        withBooks.addBook(book);
        authors.save(withBooks);

        Author without = new Author();
        without.setName("Without");
        authors.save(without);

        assertThat(authors.query().whereHas("books").get())
                .extracting(Author::getName)
                .containsExactly("WithBooks");

        assertThat(authors.query().whereDoesntHave("books").get())
                .extracting(Author::getName)
                .containsExactly("Without");
    }

    @Test
    void whereHas_nestedExists_filtersByRelatedPages() {
        Author longBooks = new Author();
        longBooks.setName("Long");
        Book longBook = new Book();
        longBook.setTitle("Epic");
        longBook.setPages(250);
        longBooks.addBook(longBook);
        authors.save(longBooks);

        Author shortBooks = new Author();
        shortBooks.setName("Short");
        Book shortBook = new Book();
        shortBook.setTitle("Pamphlet");
        shortBook.setPages(40);
        shortBooks.addBook(shortBook);
        authors.save(shortBooks);

        assertThat(authors.query()
                        .whereHas("books", f -> f.whereGt("pages", 100))
                        .get())
                .extracting(Author::getName)
                .containsExactly("Long");
    }

    @Test
    void delete_returnsDeletedCount() {
        Author a = new Author();
        a.setName("Temp");
        authors.save(a);
        Author b = new Author();
        b.setName("Keep");
        authors.save(b);

        long removed = authors.query().where("name", "Temp").delete();
        assertThat(removed).isEqualTo(1L);
        assertThat(authors.query().where("name", "Temp").exists()).isFalse();
        assertThat(authors.query().where("name", "Keep").exists()).isTrue();
    }

    @Test
    void fetchCollection_withPage_throws() {
        assertThatThrownBy(() -> authors.query()
                        .fetchCollection("books")
                        .page(PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fetchCollection");
    }

    @Test
    void whereYear_whereMonth_whereDate_smoke() {
        Author jul2024 = new Author();
        jul2024.setName("Jul2024");
        jul2024.setCreatedAt(LocalDateTime.of(2024, 7, 15, 10, 30, 0));
        authors.save(jul2024);

        Author jan2025 = new Author();
        jan2025.setName("Jan2025");
        jan2025.setCreatedAt(LocalDateTime.of(2025, 1, 5, 8, 0, 0));
        authors.save(jan2025);

        assertThat(authors.query().whereYear("createdAt", 2024).get())
                .extracting(Author::getName)
                .containsExactly("Jul2024");

        assertThat(authors.query().whereMonth("createdAt", 7).get())
                .extracting(Author::getName)
                .containsExactly("Jul2024");

        assertThat(authors.query().whereDate("createdAt", LocalDate.of(2025, 1, 5)).get())
                .extracting(Author::getName)
                .containsExactly("Jan2025");
    }

    @Test
    void whereColumn_comparesScoreAndThreshold() {
        Author equal = new Author();
        equal.setName("Equal");
        equal.setScore(10);
        equal.setThreshold(10);
        authors.save(equal);

        Author above = new Author();
        above.setName("Above");
        above.setScore(20);
        above.setThreshold(10);
        authors.save(above);

        assertThat(authors.query().whereColumn("score", "threshold").get())
                .extracting(Author::getName)
                .containsExactly("Equal");

        assertThat(authors.query().whereColumn("score", ">", "threshold").get())
                .extracting(Author::getName)
                .containsExactly("Above");
    }

    @Test
    void whereNotBetween_excludesRange() {
        Author low = authorWithScore("Low", 5);
        Author mid = authorWithScore("Mid", 15);
        Author high = authorWithScore("High", 50);
        authors.save(low);
        authors.save(mid);
        authors.save(high);

        assertThat(authors.query().whereNotBetween("score", 10, 20).get())
                .extracting(Author::getName)
                .containsExactlyInAnyOrder("Low", "High");
    }

    @Test
    void orWhereHas_orsWithNameFilter() {
        Author withBooks = new Author();
        withBooks.setName("WithBooks");
        Book book = new Book();
        book.setTitle("Spring");
        book.setPages(100);
        withBooks.addBook(book);
        authors.save(withBooks);

        Author named = new Author();
        named.setName("Special");
        authors.save(named);

        Author other = new Author();
        other.setName("Other");
        authors.save(other);

        assertThat(authors.query()
                        .where("name", "Special")
                        .orWhereHas("books")
                        .get())
                .extracting(Author::getName)
                .containsExactlyInAnyOrder("Special", "WithBooks");
    }

    @Test
    void whereRelation_filtersByRelatedTitle() {
        Author match = new Author();
        match.setName("Match");
        Book spring = new Book();
        spring.setTitle("Spring");
        spring.setPages(120);
        match.addBook(spring);
        authors.save(match);

        Author other = new Author();
        other.setName("Other");
        Book hibernate = new Book();
        hibernate.setTitle("Hibernate");
        hibernate.setPages(90);
        other.addBook(hibernate);
        authors.save(other);

        assertThat(authors.query().whereRelation("books", "title", "Spring").get())
                .extracting(Author::getName)
                .containsExactly("Match");
    }

    @Test
    void firstOrFail_throwsWhenEmpty_andSucceedsWhenPresent() {
        assertThatThrownBy(() -> authors.query().where("name", "Missing").firstOrFail())
                .isInstanceOf(FluentQueryNotFoundException.class)
                .hasMessageContaining("No result found");

        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        assertThat(authors.query().where("name", "Ada").firstOrFail().getName()).isEqualTo("Ada");
    }

    @Test
    void oneOrFail_and_paginate_and_whereEndsWith() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        Author grace = new Author();
        grace.setName("Grace");
        authors.save(grace);

        assertThat(authors.query().where("name", "Ada").oneOrFail().getName()).isEqualTo("Ada");
        assertThatThrownBy(() -> authors.query().where("name", "Missing").oneOrFail())
                .isInstanceOf(FluentQueryNotFoundException.class);

        assertThat(authors.query().orderByAsc("name").paginate(0, 1).getContent())
                .extracting(Author::getName)
                .containsExactly("Ada");

        assertThat(authors.query().whereEndsWith("name", "ce").get())
                .extracting(Author::getName)
                .containsExactly("Grace");

        assertThat(authors.query().optionalWhereContains("name", "  ").get()).hasSize(2);
    }

    @Test
    void whereIn_empty_matchesNothing_and_when_else_branch() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        assertThat(authors.query().whereIn("name", java.util.List.of()).get()).isEmpty();

        assertThat(authors.query()
                        .when(false,
                                q -> q.where("name", "Missing"),
                                q -> q.where("name", "Ada"))
                        .get())
                .extracting(Author::getName)
                .containsExactly("Ada");
    }

    @Test
    void select_withInterfaceProjection() {
        Author ada = new Author();
        ada.setName("Ada");
        ada.setScore(10);
        ada.setThreshold(5);
        authors.save(ada);

        interface AuthorName {
            Long getId();
            String getName();
        }

        AuthorName projected = authors.query()
                .where("name", "Ada")
                .select("id", "name")
                .firstAs(AuthorName.class)
                .orElseThrow();

        assertThat(projected.getName()).isEqualTo("Ada");
        assertThat(projected.getId()).isNotNull();
    }

    @Test
    void unless_skipsWhenConditionTrue() {
        Author ada = new Author();
        ada.setName("Ada");
        authors.save(ada);

        Author grace = new Author();
        grace.setName("Grace");
        authors.save(grace);

        assertThat(authors.query()
                        .unless(true, q -> q.where("name", "Ada"))
                        .get())
                .hasSize(2);

        assertThat(authors.query()
                        .unless(false, q -> q.where("name", "Ada"))
                        .get())
                .extracting(Author::getName)
                .containsExactly("Ada");
    }

    private static Author authorWithScore(String name, int score) {
        Author a = new Author();
        a.setName(name);
        a.setScore(score);
        return a;
    }
}
