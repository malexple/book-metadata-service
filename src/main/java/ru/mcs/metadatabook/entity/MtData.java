package ru.mcs.metadatabook.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Главное хранилище (heap) — flex-колонки value0..value49.
 * Соответствует ТОЧНО схеме V2__create_data_and_indexes.sql: без partition,
 * без created_by/modified_by/deleted_by (их нет в лаконичной схеме — раньше
 * они попали сюда по инерции из демо-проекта Pivot и ломали INSERT/UPDATE).
 */
@Entity
@Table(name = "mt_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtData {

    @EmbeddedId
    @Builder.Default
    private MtDataId id = new MtDataId();

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "name")
    private String name;

    @Column(name = "value0", length = 4000) private String value0;
    @Column(name = "value1", length = 4000) private String value1;
    @Column(name = "value2", length = 4000) private String value2;
    @Column(name = "value3", length = 4000) private String value3;
    @Column(name = "value4", length = 4000) private String value4;
    @Column(name = "value5", length = 4000) private String value5;
    @Column(name = "value6", length = 4000) private String value6;
    @Column(name = "value7", length = 4000) private String value7;
    @Column(name = "value8", length = 4000) private String value8;
    @Column(name = "value9", length = 4000) private String value9;
    @Column(name = "value10", length = 4000) private String value10;
    @Column(name = "value11", length = 4000) private String value11;
    @Column(name = "value12", length = 4000) private String value12;
    @Column(name = "value13", length = 4000) private String value13;
    @Column(name = "value14", length = 4000) private String value14;
    @Column(name = "value15", length = 4000) private String value15;
    @Column(name = "value16", length = 4000) private String value16;
    @Column(name = "value17", length = 4000) private String value17;
    @Column(name = "value18", length = 4000) private String value18;
    @Column(name = "value19", length = 4000) private String value19;
    @Column(name = "value20", length = 4000) private String value20;
    @Column(name = "value21", length = 4000) private String value21;
    @Column(name = "value22", length = 4000) private String value22;
    @Column(name = "value23", length = 4000) private String value23;
    @Column(name = "value24", length = 4000) private String value24;
    @Column(name = "value25", length = 4000) private String value25;
    @Column(name = "value26", length = 4000) private String value26;
    @Column(name = "value27", length = 4000) private String value27;
    @Column(name = "value28", length = 4000) private String value28;
    @Column(name = "value29", length = 4000) private String value29;
    @Column(name = "value30", length = 4000) private String value30;
    @Column(name = "value31", length = 4000) private String value31;
    @Column(name = "value32", length = 4000) private String value32;
    @Column(name = "value33", length = 4000) private String value33;
    @Column(name = "value34", length = 4000) private String value34;
    @Column(name = "value35", length = 4000) private String value35;
    @Column(name = "value36", length = 4000) private String value36;
    @Column(name = "value37", length = 4000) private String value37;
    @Column(name = "value38", length = 4000) private String value38;
    @Column(name = "value39", length = 4000) private String value39;
    @Column(name = "value40", length = 4000) private String value40;
    @Column(name = "value41", length = 4000) private String value41;
    @Column(name = "value42", length = 4000) private String value42;
    @Column(name = "value43", length = 4000) private String value43;
    @Column(name = "value44", length = 4000) private String value44;
    @Column(name = "value45", length = 4000) private String value45;
    @Column(name = "value46", length = 4000) private String value46;
    @Column(name = "value47", length = 4000) private String value47;
    @Column(name = "value48", length = 4000) private String value48;
    @Column(name = "value49", length = 4000) private String value49;

    @Type(JsonType.class)
    @Column(name = "sources_meta", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> sourcesMeta = new HashMap<>();

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "modified_at")
    @Builder.Default
    private OffsetDateTime modifiedAt = OffsetDateTime.now();

    public Integer getOrgId() {
        return id != null ? id.getOrgId() : null;
    }

    public UUID getGuid() {
        return id != null ? id.getGuid() : null;
    }

    public void setOrgId(Integer orgId) {
        if (id == null) id = new MtDataId();
        id.setOrgId(orgId);
    }

    public void setGuid(UUID guid) {
        if (id == null) id = new MtDataId();
        id.setGuid(guid);
    }

    public String getValue(int slot) {
        return switch (slot) {
            case 0 -> value0; case 1 -> value1; case 2 -> value2; case 3 -> value3; case 4 -> value4;
            case 5 -> value5; case 6 -> value6; case 7 -> value7; case 8 -> value8; case 9 -> value9;
            case 10 -> value10; case 11 -> value11; case 12 -> value12; case 13 -> value13; case 14 -> value14;
            case 15 -> value15; case 16 -> value16; case 17 -> value17; case 18 -> value18; case 19 -> value19;
            case 20 -> value20; case 21 -> value21; case 22 -> value22; case 23 -> value23; case 24 -> value24;
            case 25 -> value25; case 26 -> value26; case 27 -> value27; case 28 -> value28; case 29 -> value29;
            case 30 -> value30; case 31 -> value31; case 32 -> value32; case 33 -> value33; case 34 -> value34;
            case 35 -> value35; case 36 -> value36; case 37 -> value37; case 38 -> value38; case 39 -> value39;
            case 40 -> value40; case 41 -> value41; case 42 -> value42; case 43 -> value43; case 44 -> value44;
            case 45 -> value45; case 46 -> value46; case 47 -> value47; case 48 -> value48; case 49 -> value49;
            default -> throw new IllegalArgumentException("Invalid slot: " + slot);
        };
    }

    public void setValue(int slot, String value) {
        switch (slot) {
            case 0 -> value0 = value; case 1 -> value1 = value; case 2 -> value2 = value;
            case 3 -> value3 = value; case 4 -> value4 = value; case 5 -> value5 = value;
            case 6 -> value6 = value; case 7 -> value7 = value; case 8 -> value8 = value;
            case 9 -> value9 = value; case 10 -> value10 = value; case 11 -> value11 = value;
            case 12 -> value12 = value; case 13 -> value13 = value; case 14 -> value14 = value;
            case 15 -> value15 = value; case 16 -> value16 = value; case 17 -> value17 = value;
            case 18 -> value18 = value; case 19 -> value19 = value; case 20 -> value20 = value;
            case 21 -> value21 = value; case 22 -> value22 = value; case 23 -> value23 = value;
            case 24 -> value24 = value; case 25 -> value25 = value; case 26 -> value26 = value;
            case 27 -> value27 = value; case 28 -> value28 = value; case 29 -> value29 = value;
            case 30 -> value30 = value; case 31 -> value31 = value; case 32 -> value32 = value;
            case 33 -> value33 = value; case 34 -> value34 = value; case 35 -> value35 = value;
            case 36 -> value36 = value; case 37 -> value37 = value; case 38 -> value38 = value;
            case 39 -> value39 = value; case 40 -> value40 = value; case 41 -> value41 = value;
            case 42 -> value42 = value; case 43 -> value43 = value; case 44 -> value44 = value;
            case 45 -> value45 = value; case 46 -> value46 = value; case 47 -> value47 = value;
            case 48 -> value48 = value; case 49 -> value49 = value;
            default -> throw new IllegalArgumentException("Invalid slot: " + slot);
        }
    }

    /** Проставляет provenance для конкретного слота. Вызывать сразу после setValue(slot, ...). */
    public void putSource(int slot, String source, Double confidence) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", source);
        meta.put("updated_at", OffsetDateTime.now().toString());
        if (confidence != null) meta.put("confidence", confidence);
        if (sourcesMeta == null) sourcesMeta = new HashMap<>();
        sourcesMeta.put(String.valueOf(slot), meta);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSourceInfo(int slot) {
        if (sourcesMeta == null) return null;
        return (Map<String, Object>) sourcesMeta.get(String.valueOf(slot));
    }
}