package dev.benjaminor.fluentquery.jpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapsIdPersistableTest {

    static final class Sample extends MapsIdPersistable<Long> {
        private Long id;

        Sample(Long id) {
            this.id = id;
        }

        void setId(Long id) {
            this.id = id;
        }

        @Override
        protected Long mapsIdValue() {
            return id;
        }

        void simulatePostLoad() {
            markNotNew();
        }
    }

    @Test
    void isNew_untilPostLoad() {
        Sample s = new Sample(null);
        assertThat(s.isNew()).isTrue();
        assertThat(s.getId()).isNull();

        s.setId(10L);
        assertThat(s.getId()).isEqualTo(10L);
        assertThat(s.isNew()).isTrue();

        s.simulatePostLoad();
        assertThat(s.isNew()).isFalse();
    }
}
