-- V4__support_tables.sql
-- Служебные таблицы: очередь "не найдено", вложения PDF/DjVu, API-ключи.

CREATE TABLE unresolved_lookup (
    id                BIGSERIAL PRIMARY KEY,
    org_id            INTEGER NOT NULL DEFAULT 1 REFERENCES organizations(org_id),
    isbn              VARCHAR(20),
    title             VARCHAR(1000),
    author_name       VARCHAR(500),
    attempted_sources TEXT[] NOT NULL DEFAULT '{}',
    status            VARCHAR(20) NOT NULL DEFAULT 'pending', -- pending / reviewed / resolved / discarded
    raw_context       JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_unresolved_lookup_status ON unresolved_lookup (org_id, status);
CREATE INDEX idx_unresolved_lookup_isbn ON unresolved_lookup (isbn);

CREATE TABLE mt_attachment (
    id             BIGSERIAL PRIMARY KEY,
    org_id         INTEGER NOT NULL DEFAULT 1 REFERENCES organizations(org_id),
    obj_id         INTEGER NOT NULL REFERENCES mt_objects(obj_id),
    guid           UUID,
    file_name      VARCHAR(500) NOT NULL,
    file_format    VARCHAR(10)  NOT NULL,
    storage_path   VARCHAR(1000) NOT NULL,
    ocr_status     VARCHAR(20)  NOT NULL DEFAULT 'pending',
    ocr_error      TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

CREATE INDEX idx_mt_attachment_status ON mt_attachment (org_id, ocr_status);
CREATE INDEX idx_mt_attachment_guid ON mt_attachment (org_id, guid);

CREATE TABLE api_keys (
    id          BIGSERIAL PRIMARY KEY,
    org_id      INTEGER NOT NULL DEFAULT 1 REFERENCES organizations(org_id),
    key_value   VARCHAR(200) NOT NULL UNIQUE,
    label       VARCHAR(255),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO api_keys (org_id, key_value, label) VALUES
    (1, 'dev-local-key-change-me', 'Local dev key');
