-- V1__create_core_metadata.sql
-- Ядро метаданных: organizations, mt_objects, mt_fields.
-- Упрощено относительно демо-версии Pivot: без multi-org партиционирования,
-- без служебных полей (formula/autonumber/picklist_values), которые
-- в книжном сервисе пока не используются.

CREATE TABLE organizations (
    org_id       SERIAL PRIMARY KEY,
    org_name     VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    modified_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Единственный тенант на старте (см. book.pivot.default-org-id в application.yml)
INSERT INTO organizations (org_id, org_name, display_name) VALUES
    (1, 'default', 'Default Organization');
SELECT setval('organizations_org_id_seq', 1);

CREATE TABLE mt_objects (
    obj_id       SERIAL PRIMARY KEY,
    org_id       INTEGER NOT NULL REFERENCES organizations(org_id),
    obj_name     VARCHAR(255) NOT NULL,
    label        VARCHAR(255),
    plural_label VARCHAR(255),
    description  TEXT,
    is_custom    BOOLEAN DEFAULT TRUE,
    is_active    BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    created_by   UUID,
    modified_at  TIMESTAMPTZ DEFAULT NOW(),
    modified_by  UUID,
    UNIQUE (org_id, obj_name)
);

CREATE INDEX idx_mt_objects_org ON mt_objects(org_id);

CREATE TABLE mt_fields (
    field_id            SERIAL PRIMARY KEY,
    org_id              INTEGER NOT NULL REFERENCES organizations(org_id),
    obj_id              INTEGER NOT NULL REFERENCES mt_objects(obj_id) ON DELETE CASCADE,
    field_name          VARCHAR(255) NOT NULL,
    label               VARCHAR(255),
    -- text, number, date, datetime, boolean, picklist, lookup, email, url, phone
    data_type           VARCHAR(50) NOT NULL,
    field_num           INTEGER NOT NULL,          -- слот value0..value49 в mt_data

    is_indexed          BOOLEAN DEFAULT FALSE,
    is_unique           BOOLEAN DEFAULT FALSE,
    is_required         BOOLEAN DEFAULT FALSE,

    -- для lookup-полей (например, AuthorId -> Author)
    reference_obj_id    INTEGER REFERENCES mt_objects(obj_id),
    relationship_type   VARCHAR(20),

    length              INTEGER,

    created_at          TIMESTAMPTZ DEFAULT NOW(),
    modified_at         TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE (org_id, obj_id, field_name),
    UNIQUE (org_id, obj_id, field_num)
);

CREATE INDEX idx_mt_fields_org_obj ON mt_fields(org_id, obj_id);
CREATE INDEX idx_mt_fields_indexed ON mt_fields(org_id, obj_id) WHERE is_indexed = TRUE;
