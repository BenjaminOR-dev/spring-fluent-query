package dev.benjaminor.fluentquery.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityLifecycleRegistryTest {

    @Test
    void exactTypeMatch_only() {
        RecordingListener<Parent> parentListener = new RecordingListener<>(Parent.class);
        EntityLifecycleRegistry registry =
                new EntityLifecycleRegistry(List.of(parentListener), true);

        assertThat(registry.listenersFor(Parent.class)).hasSize(1);
        assertThat(registry.listenersFor(Child.class)).isEmpty();

        Child child = new Child();
        registry.fireOnSaving(child);
        assertThat(parentListener.events).isEmpty();

        Parent parent = new Parent();
        registry.fireOnSaving(parent);
        assertThat(parentListener.events).containsExactly("onSaving");
    }

    @Test
    void illegalStateFromHook_wrappedAsLifecycleException() {
        EntityLifecycleListener<Parent> failing = new EntityLifecycleListener<>() {
            @Override
            public Class<Parent> entityType() {
                return Parent.class;
            }

            @Override
            public void onSaving(Parent entity) {
                throw new IllegalStateException("nope");
            }
        };
        EntityLifecycleRegistry registry = new EntityLifecycleRegistry(List.of(failing), true);

        assertThatThrownBy(() -> registry.fireOnSaving(new Parent()))
                .isInstanceOf(FluentQueryLifecycleException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessage("nope");
    }

    @Test
    void disabled_isNoOp() {
        RecordingListener<Parent> listener = new RecordingListener<>(Parent.class);
        EntityLifecycleRegistry registry =
                new EntityLifecycleRegistry(List.of(listener), false);

        registry.fireOnSaving(new Parent());
        registry.fireOnCreated(new Parent());
        assertThat(listener.events).isEmpty();
        assertThat(registry.isEnabled()).isFalse();
    }

    @Test
    void noop_factory() {
        EntityLifecycleRegistry noop = EntityLifecycleRegistry.noop();
        assertThat(noop.isEnabled()).isFalse();
        assertThat(noop.listenersFor(Parent.class)).isEmpty();
    }

    static class Parent {
    }

    static class Child extends Parent {
    }

    static final class RecordingListener<T> implements EntityLifecycleListener<T> {
        private final Class<T> type;
        final List<String> events = new ArrayList<>();

        RecordingListener(Class<T> type) {
            this.type = type;
        }

        @Override
        public Class<T> entityType() {
            return type;
        }

        @Override
        public void onSaving(T entity) {
            events.add("onSaving");
        }

        @Override
        public void onCreated(T entity) {
            events.add("onCreated");
        }
    }
}
