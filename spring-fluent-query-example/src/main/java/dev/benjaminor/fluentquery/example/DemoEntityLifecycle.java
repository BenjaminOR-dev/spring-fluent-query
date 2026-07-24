package dev.benjaminor.fluentquery.example;

import dev.benjaminor.fluentquery.lifecycle.AbstractEntityLifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demo lifecycle listener for {@link DemoEntity} (optional hooks — not Active Record).
 */
@Component
class DemoEntityLifecycle extends AbstractEntityLifecycleListener<DemoEntity> {

    private static final Logger log = LoggerFactory.getLogger(DemoEntityLifecycle.class);

    @Override
    public void onCreated(DemoEntity entity) {
        log.info("lifecycle onCreated id={} name={}", entity.getId(), entity.getName());
    }

    @Override
    public void onUpdated(DemoEntity entity) {
        log.info("lifecycle onUpdated id={} name={}", entity.getId(), entity.getName());
    }

    @Override
    public void onDeleted(DemoEntity entity) {
        log.info("lifecycle onDeleted id={} name={}", entity.getId(), entity.getName());
    }
}
