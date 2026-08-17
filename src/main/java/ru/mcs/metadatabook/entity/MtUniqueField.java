package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mt_unique_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtUniqueField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idx_id")
    private Long idxId;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "field_num", nullable = false)
    private Integer fieldNum;

    @Column(name = "guid", nullable = false)
    private UUID guid;

    @Column(name = "string_value")
    private String stringValue;

    @Column(name = "num_value", precision = 18, scale = 6)
    private BigDecimal numValue;

    @Column(name = "date_value")
    private OffsetDateTime dateValue;
}