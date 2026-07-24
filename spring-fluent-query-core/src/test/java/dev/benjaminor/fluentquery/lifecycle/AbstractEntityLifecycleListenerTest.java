package dev.benjaminor.fluentquery.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractEntityLifecycleListenerTest {

    @Test
    void resolvesEntityTypeFromGeneric() {
        DemoListener listener = new DemoListener();
        assertThat(listener.entityType()).isEqualTo(Demo.class);
    }

    @Test
    void failsWhenTypeNotConcrete() {
        assertThatThrownBy(RawListener::new)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve entity type");
    }

    static final class Demo {
    }

    static final class DemoListener extends AbstractEntityLifecycleListener<Demo> {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class RawListener extends AbstractEntityLifecycleListener {
        RawListener() {
            super();
        }
    }
}
