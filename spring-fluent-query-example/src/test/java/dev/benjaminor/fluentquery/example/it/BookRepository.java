package dev.benjaminor.fluentquery.example.it;

import dev.benjaminor.fluentquery.FluentQueryRepository;

/** Repository for {@link Book} integration tests. */
public interface BookRepository extends FluentQueryRepository<Book, Long> {
}
