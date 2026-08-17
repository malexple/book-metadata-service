package ru.mcs.metadatabook.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
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
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Метаданные поля объекта. field_num — номер flex-слота (value0..value49) в mt_data.
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

    @Column(name = "is_external_id")
    @Builder.Default
    private Boolean isExternalId = false;

    @Column(name = "relationship_name")
    private String relationshipName;

    @Column(name = "reference_obj_id")
    private Integer referenceObjId;

    @Column(name = "relationship_type")
    private String relationshipType;

    @Column(name = "delete_constraint")
    private String deleteConstraint;

    @Type(StringArrayType.class)
    @Column(name = "picklist_values", columnDefinition = "text[]")
    private String[] picklistValues;

    @Column(name = "formula_expression")
    private String formulaExpression;

    @Column(name = "formula_return_type")
    private String formulaReturnType;

    @Column(name = "autonumber_format")
    private String autonumberFormat;

    @Column(name = "autonumber_start")
    private Integer autonumberStart;

    @Column(name = "validation_rule")
    private String validationRule;

    @Column(name = "validation_message")
    private String validationMessage;

    @Column(name = "length")
    private Integer length;

    @Column(name = "precision")
    private Integer precision;

    @Column(name = "scale")
    private Integer scale;

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