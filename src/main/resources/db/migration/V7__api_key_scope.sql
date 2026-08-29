-- V7__api_key_scope.sql
-- Разграничение доступа для публичного запуска сервиса.
-- PUBLIC_READ   - только чтение через /api/v1/public/*, используется внутренним
--                 прокси-ключом сайта, никогда не выдаётся напрямую посетителям
-- PARTNER_WRITE - создание/обогащение карточек (POST /books, /enrichment/public/*)
-- ADMIN         - модерация LLM-предложений, /api/metadata/**, служебные операции

ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'PARTNER_WRITE';

-- Существующий dev-ключ оставляем с полным доступом, чтобы не сломать локальную разработку.
UPDATE api_keys SET scope = 'ADMIN' WHERE key_value = 'dev-local-key-change-me';

-- Внутренний публичный ключ для анонимного поиска с сайта.
-- Значение переопределяется в проде через book.api.public-api-key (см. application.yml) -
-- строка ниже - лишь дефолт для локальной разработки, чтобы стенд поднимался "из коробки".
INSERT INTO api_keys (org_id, key_value, label, scope)
VALUES (1, 'public-books-2024-change-me', 'Public web search (internal use only)', 'PUBLIC_READ')
ON CONFLICT (key_value) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_api_keys_scope ON api_keys(scope);