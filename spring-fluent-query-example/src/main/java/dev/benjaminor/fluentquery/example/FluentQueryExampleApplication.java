package dev.benjaminor.fluentquery.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Minimal demo application for spring-fluent-query (H2 + {@link DemoEntityRepository}).
 * On startup runs a full Create / Read / Update / Delete sample via {@link DemoCrudService}.
 */
@SpringBootApplication
public class FluentQueryExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(FluentQueryExampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(FluentQueryExampleApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(DemoCrudService crud) {
        return args -> {
            // CREATE
            DemoEntity ada = crud.create(1L, "Ada");
            crud.create(2L, "Grace");
            log.info("CREATE → id={}, name={}", ada.getId(), ada.getName());

            // READ
            var found = crud.readByName("Ada");
            log.info("READ where name=Ada → {}", found.map(DemoEntity::getName).orElse(null));
            log.info("SEARCH like 'a' → {}", crud.search("a").stream().map(DemoEntity::getName).toList());

            // UPDATE
            var updated = crud.updateName(1L, "Ada Lovelace");
            log.info("UPDATE id=1 → {}", updated.map(DemoEntity::getName).orElse(null));

            // DELETE one (by id) vs many (by filter — deletes every match)
            crud.create(3L, "Grace");
            boolean one = crud.deleteById(2L);
            long many = crud.deleteAllByName("Grace");
            log.info("DELETE one id=2 → {} | DELETE many name=Grace → removed={}", one, many);
            log.info("count after delete → {}", crud.search(null).size());
        };
    }
}
