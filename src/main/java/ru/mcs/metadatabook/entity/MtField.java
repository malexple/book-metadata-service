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

/**
 * Метаданные поля объекта. field_num — номер flex-слота (value0..value49) в mt_data.
 * Соответствует ТОЧНО схеме V1__create_core_metadata.sql — без formula/autonumber/
 * validation/picklist полей, которые были в исходном демо-проекте Pivot, но не вошли
 * в упрощённую схему книжного сервиса.
 */
@Entity
@Table(name = "mt_fields", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"org_id", "obj_id", "field_name"}),
        @UniqueConstraint(columnNames = {"org_id", "obj_id", "field_num"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "field_id")
    private Integer fieldId;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "label")
    private String label;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "field_num", nullable = false)
    private Integer fieldNum;

    @Column(name = "is_indexed")
    @Builder.Default
    private Boolean isIndexed = false;

    @Column(name = "is_unique")
    @Builder.Default
    private Boolean isUnique = false;

    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = false;

    @Column(name = "reference_obj_id")
    private Integer referenceObjId;

    @Column(name = "relationship_type")
    private String relationshipType;

    @Column(name = "length")
    private Integer length;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "modified_at")
    @Builder.Default
    private OffsetDateTime modifiedAt = OffsetDateTime.now();
}