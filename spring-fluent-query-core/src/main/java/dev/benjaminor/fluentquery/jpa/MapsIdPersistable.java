package dev.benjaminor.fluentquery.jpa;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Locale- and schema-neutral {@link Persistable} base for {@code @MapsId} 1:1 entities
 * (shared PK with a parent). Prevents Spring Data {@code save()} from calling {@code merge()}
 * on a brand-new instance whose id is still unset / not yet considered “persistent”.
 *
 * <p>No audit columns here — hosts map {@code createdAt}/{@code updatedAt} (or local
 * column names) with their own {@code @MappedSuperclass} or {@code @AttributeOverride}.
 *
 * <pre>{@code
 * @MappedSuperclass
 * public abstract class MyMapsIdEntity extends MapsIdPersistable<Long> {
 *     @Id
 *     private Long id;
 *     @OneToOne @MapsId
 *     private Parent parent;
 *
 *     @Override
 *     protected Long mapsIdValue() {
 *         return id;
 *     }
 * }
 * }</pre>
 *
 * @param <ID> primary-key type ({@link Long}, {@link String}, {@link java.util.UUID}, …)
 */
@MappedSuperclass
public abstract class MapsIdPersistable<ID> implements Persistable<ID> {

    /**
     * {@code true} until the instance is loaded or persisted.
     */
    @Transient
    private boolean newEntity = true;

    /**
     * Shared primary key value ({@code @MapsId} / {@code @Id} on the concrete entity).
     *
     * @return id, may be {@code null} before assignment
     */
    protected abstract ID mapsIdValue();

    @Override
    public ID getId() {
        return mapsIdValue();
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    protected void markNotNew() {
        this.newEntity = false;
    }

    /**
     * Marks the instance as new again (rare; e.g. copy-as-insert flows).
     */
    protected void markNew() {
        this.newEntity = true;
    }
}
