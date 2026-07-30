package dev.benjaminor.fluentquery.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal HTTP demo: respond with a FluentQuery projection ({@code oneOrFail(Class)}),
 * not a JPA entity (avoids lazy graphs / JSON cycles).
 */
@RestController
@RequestMapping("/api/demos")
public class DemoApiController {

    private final DemoEntityRepository repository;

    public DemoApiController(DemoEntityRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemoSummary> getById(@PathVariable Long id) {
        DemoSummary summary = repository.query()
                .where("id", id)
                .select("id", "name")
                .oneOrFail(DemoSummary.class);
        return ResponseEntity.ok(summary);
    }
}
