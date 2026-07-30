package dev.benjaminor.fluentquery.example;

/**
 * Lean API projection for {@link DemoEntity} — columns only, no JPA graph.
 */
public interface DemoSummary {

    Long getId();

    String getName();
}
