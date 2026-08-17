package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mt_objects", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"org_id", "obj_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "obj_id")
    private Integer objId;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "obj_name", nullable = false)
    private String objName;

    @Column(name = "label")
    private String label;

    @Column(name = "plural_label")
    private String pluralLabel;

    @Column(name = "description")
    private String description;

    @Column(name = "is_custom")
    @Builder.Default
    private Boolean isCustom = true;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "modified_at")
    @Builder.Default
    private OffsetDateTime modifiedAt = OffsetDateTime.now();

    @Column(name = "modified_by")
    private UUID modifiedBy;
}