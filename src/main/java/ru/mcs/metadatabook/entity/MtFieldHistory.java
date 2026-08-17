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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * История изменений value-слотов. Используется при повторном обогащении
 * (scheduler находит новое значение из Litres/Ozon поверх уже существующего).
 */
@Entity
@Table(name = "mt_field_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtFieldHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "org_id", nullable = false)
    private Integer orgId;

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "guid", nullable = false)
    private UUID guid;

    @Column(name = "field_num", nullable = false)
    private Integer fieldNum;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "changed_at")
    @Builder.Default
    private OffsetDateTime changedAt = OffsetDateTime.now();

    @Column(name = "changed_by")
    private UUID changedBy;
}