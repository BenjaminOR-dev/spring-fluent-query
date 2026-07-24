package dev.benjaminor.fluentquery.lifecycle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FluentQueryJpaRepositoryTest {

    JpaEntityInformation<Sample, Long> entityInformation;
    EntityManager entityManager;
    RecordingListener listener;
    FluentQueryJpaRepository<Sample, Long> repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityInformation = mock(JpaEntityInformation.class);
        entityManager = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(entityManager.getEntityManagerFactory()).thenReturn(emf);
        when(entityInformation.getJavaType()).thenReturn(Sample.class);

        listener = new RecordingListener();
        EntityLifecycleRegistry registry =
                new EntityLifecycleRegistry(List.of(listener), true);
        repository = new FluentQueryJpaRepository<>(entityInformation, entityManager, registry);
    }

    @Test
    void saveNew_firesCreatingChain() {
        Sample entity = new Sample(null);
        when(entityInformation.isNew(entity)).thenReturn(true);
        doAnswer(inv -> {
            Sample s = inv.getArgument(0);
            s.id = 1L;
            return null;
        }).when(entityManager).persist(any());

        Sample saved = repository.save(entity);

        assertThat(saved.id).isEqualTo(1L);
        assertThat(listener.events)
                .containsExactly("onSaving", "onCreating", "onCreated", "onSaved");
        verify(entityManager).persist(entity);
        verify(entityManager, never()).merge(any());
    }

    @Test
    void saveExisting_firesUpdatingChain() {
        Sample entity = new Sample(5L);
        when(entityInformation.isNew(entity)).thenReturn(false);
        when(entityManager.merge(entity)).thenReturn(entity);

        repository.save(entity);

        assertThat(listener.events)
                .containsExactly("onSaving", "onUpdating", "onUpdated", "onSaved");
        verify(entityManager).merge(entity);
        verify(entityManager, never()).persist(any());
    }

    @Test
    void saveAll_firesPerItem() {
        Sample a = new Sample(null);
        Sample b = new Sample(null);
        Sample c = new Sample(null);
        when(entityInformation.isNew(any())).thenReturn(true);

        repository.saveAll(List.of(a, b, c));

        long created = listener.events.stream().filter("onCreated"::equals).count();
        assertThat(created).isEqualTo(3);
        assertThat(listener.events).hasSize(12); // 4 hooks × 3
    }

    @Test
    void delete_firesDeletingDeleted() {
        Sample entity = new Sample(9L);
        when(entityInformation.isNew(entity)).thenReturn(false);
        when(entityManager.contains(entity)).thenReturn(true);

        repository.delete(entity);

        assertThat(listener.events).containsExactly("onDeleting", "onDeleted");
        verify(entityManager).remove(entity);
    }

    @Test
    void deleteNew_skipsHooksAndRemove() {
        Sample entity = new Sample(null);
        when(entityInformation.isNew(entity)).thenReturn(true);

        repository.delete(entity);

        assertThat(listener.events).isEmpty();
        verify(entityManager, never()).remove(any());
    }

    @Test
    void deleteMissing_skipsHooks() {
        Sample entity = new Sample(42L);
        when(entityInformation.isNew(entity)).thenReturn(false);
        when(entityManager.contains(entity)).thenReturn(false);
        when(entityInformation.getId(entity)).thenReturn(42L);
        when(entityManager.find(Sample.class, 42L)).thenReturn(null);

        repository.delete(entity);

        assertThat(listener.events).isEmpty();
        verify(entityManager, never()).remove(any());
        verify(entityManager, never()).merge(any());
    }

    @Test
    void onCreating_throws_abortsPersist() {
        Sample entity = new Sample(null);
        when(entityInformation.isNew(entity)).thenReturn(true);
        EntityLifecycleListener<Sample> failing = new EntityLifecycleListener<>() {
            @Override
            public Class<Sample> entityType() {
                return Sample.class;
            }

            @Override
            public void onCreating(Sample e) {
                throw new IllegalStateException("blocked-creating");
            }
        };
        FluentQueryJpaRepository<Sample, Long> repo = new FluentQueryJpaRepository<>(
                entityInformation,
                entityManager,
                new EntityLifecycleRegistry(List.of(failing), true));

        assertThatThrownBy(() -> repo.save(entity))
                .isInstanceOf(FluentQueryLifecycleException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("blocked-creating");
        verify(entityManager, never()).persist(any());
        verify(entityManager, never()).merge(any());
    }

    @Test
    void onCreating_mutatesId_stillPersistsFromIsNewSnapshot() {
        Sample entity = new Sample(null);
        when(entityInformation.isNew(entity)).thenReturn(true);
        EntityLifecycleListener<Sample> mutating = new EntityLifecycleListener<>() {
            @Override
            public Class<Sample> entityType() {
                return Sample.class;
            }

            @Override
            public void onCreating(Sample e) {
                e.id = 99L;
            }
        };
        FluentQueryJpaRepository<Sample, Long> repo = new FluentQueryJpaRepository<>(
                entityInformation,
                entityManager,
                new EntityLifecycleRegistry(List.of(mutating), true));

        repo.save(entity);

        verify(entityManager).persist(entity);
        verify(entityManager, never()).merge(any());
        assertThat(entity.id).isEqualTo(99L);
    }

    @Test
    void disabledRegistry_behaviorUnchanged_noHooks() {
        Sample entity = new Sample(null);
        when(entityInformation.isNew(entity)).thenReturn(true);
        FluentQueryJpaRepository<Sample, Long> quiet =
                new FluentQueryJpaRepository<>(
                        entityInformation, entityManager, EntityLifecycleRegistry.noop());

        quiet.save(entity);

        assertThat(listener.events).isEmpty();
        verify(entityManager).persist(entity);
    }

    static final class Sample {
        Long id;

        Sample(Long id) {
            this.id = id;
        }
    }

    static final class RecordingListener implements EntityLifecycleListener<Sample> {
        final List<String> events = new ArrayList<>();

        @Override
        public Class<Sample> entityType() {
            return Sample.class;
        }

        @Override
        public void onSaving(Sample entity) {
            events.add("onSaving");
        }

        @Override
        public void onCreating(Sample entity) {
            events.add("onCreating");
        }

        @Override
        public void onCreated(Sample entity) {
            events.add("onCreated");
        }

        @Override
        public void onUpdating(Sample entity) {
            events.add("onUpdating");
        }

        @Override
        public void onUpdated(Sample entity) {
            events.add("onUpdated");
        }

        @Override
        public void onSaved(Sample entity) {
            events.add("onSaved");
        }

        @Override
        public void onDeleting(Sample entity) {
            events.add("onDeleting");
        }

        @Override
        public void onDeleted(Sample entity) {
            events.add("onDeleted");
        }
    }
}
