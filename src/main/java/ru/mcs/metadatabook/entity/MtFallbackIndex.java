package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mt_fallback_index")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtFallbackIndex {

    @EmbeddedId
    private MtFallbackIndexId id;

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "name")
    private String name;

    @Column(name = "name_lower")
    private String nameLower;

    @Column(name = "modified_at")
    @Builder.Default
    private OffsetDateTime modifiedAt = OffsetDateTime.now();

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class MtFallbackIndexId implements Serializable {
        @Column(name = "org_id")
        private Integer orgId;

        @Column(name = "guid")
        private UUID guid;
    }
}