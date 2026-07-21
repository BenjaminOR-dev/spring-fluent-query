package dev.benjaminor.fluentquery.example;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Simple JPA entity used by the example module. */
@Entity
public class DemoEntity {

    @Id
    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
