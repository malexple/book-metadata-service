-- V2__create_data_and_indexes.sql
-- Хранилище записей (mt_data) + типизированный индекс (mt_indexes) + история (mt_field_history).
-- Без PARTITION BY: одному тенанту физическое партиционирование не даёт выгоды,
-- а сложность и ограничения (unique-констрейнты только с partition key) остаются.
-- Если объём вырастет на порядки — вернуться к партиционированию по org_id тогда,
-- когда это станет измеримой проблемой, а не заранее.

CREATE TABLE mt_data (
    org_id       INTEGER NOT NULL REFERENCES organizations(org_id),
    guid         UUID NOT NULL DEFAULT gen_random_uuid(),
    obj_id       INTEGER NOT NULL REFERENCES mt_objects(obj_id),
    name         VARCHAR(255),

    value0 VARCHAR(4000), value1 VARCHAR(4000), value2 VARCHAR(4000), value3 VARCHAR(4000), value4 VARCHAR(4000),
    value5 VARCHAR(4000), value6 VARCHAR(4000), value7 VARCHAR(4000), value8 VARCHAR(4000), value9 VARCHAR(4000),
    value10 VARCHAR(4000), value11 VARCHAR(4000), value12 VARCHAR(4000), value13 VARCHAR(4000), value14 VARCHAR(4000),
    value15 VARCHAR(4000), value16 VARCHAR(4000), value17 VARCHAR(4000), value18 VARCHAR(4000), value19 VARCHAR(4000),
    value20 VARCHAR(4000), value21 VARCHAR(4000), value22 VARCHAR(4000), value23 VARCHAR(4000), value24 VARCHAR(4000),
    value25 VARCHAR(4000), value26 VARCHAR(4000), value27 VARCHAR(4000), value28 VARCHAR(4000), value29 VARCHAR(4000),
    value30 VARCHAR(4000), value31 VARCHAR(4000), value32 VARCHAR(4000), value33 VARCHAR(4000), value34 VARCHAR(4000),
    value35 VARCHAR(4000), value36 VARCHAR(4000), value37 VARCHAR(4000), value38 VARCHAR(4000), value39 VARCHAR(4000),
    value40 VARCHAR(4000), value41 VARCHAR(4000), value42 VARCHAR(4000), value43 VARCHAR(4000), value44 VARCHAR(4000),
    value45 VARCHAR(4000), value46 VARCHAR(4000), value47 VARCHAR(4000), value48 VARCHAR(4000), value49 VARCHAR(4000),

    -- provenance: {"<fieldNum>": {"source": "litres", "updated_at": "...", "confidence": 0.9}}
    sources_meta JSONB NOT NULL DEFAULT '{}'::jsonb,

    is_deleted   BOOLEAN DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,

    created_at   TIMESTAMPTZ DEFAULT NOW(),
    modified_at  TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (org_id, guid)
);

CREATE INDEX idx_mt_data_obj ON mt_data (org_id, obj_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_mt_data_name ON mt_data (org_id, obj_id, name);
CREATE INDEX idx_mt_data_sources_meta ON mt_data USING GIN (sources_meta);

-- Типизированный индекс для полей с is_indexed=true (поиск по ISBN/Title/Author без EAV-сканирования)
CREATE TABLE mt_indexes (
    idx_id       BIGSERIAL PRIMARY KEY,
    org_id       INTEGER NOT NULL REFERENCES organizations(org_id),
    obj_id       INTEGER NOT NULL,
    field_num    INTEGER NOT NULL,
    guid         UUID NOT NULL,

    string_value VARCHAR(255),
    num_value    NUMERIC(18,6),
    date_value   TIMESTAMPTZ
);

CREATE INDEX idx_mt_indexes_string ON mt_indexes(org_id, obj_id, field_num, string_value)
    WHERE string_value IS NOT NULL;
CREATE INDEX idx_mt_indexes_num ON mt_indexes(org_id, obj_id, field_num, num_value)
    WHERE num_value IS NOT NULL;
CREATE INDEX idx_mt_indexes_guid ON mt_indexes(org_id, guid);

-- История изменений value-слотов: нужна для версионирования при повторном обогащении
-- (scheduler находит новое значение из Litres/Ozon поверх уже существующего)
CREATE TABLE mt_field_history (
    history_id  BIGSERIAL PRIMARY KEY,
    org_id      INTEGER NOT NULL REFERENCES organizations(org_id),
    obj_id      INTEGER NOT NULL,
    guid        UUID NOT NULL,
    field_num   INTEGER NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    data_type   VARCHAR(50),
    changed_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mt_history_record ON mt_field_history(org_id, guid, changed_at DESC);
