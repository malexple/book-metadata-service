-- V6__llm_suggestions.sql
-- Очередь предложений от LLM fallback источника. Результаты LLM НЕ пишутся
-- напрямую в mt_data — они складываются сюда и ждут ручного подтверждения
-- через EnrichmentController.approveSuggestion. Это отдельный уровень защиты
-- поверх верификации внутри LlmFallbackClient (сверка полей с текстом источника):
-- даже прошедшие верификацию данные — низкой достоверности по своей природе
-- (генеративная модель, а не структурированный API), поэтому финальное решение
-- остаётся за человеком.

CREATE TABLE llm_suggestions (
    id BIGSERIAL PRIMARY KEY,
    org_id INTEGER NOT NULL DEFAULT 1 REFERENCES organizations(org_id),
    isbn VARCHAR(20),
    query_title VARCHAR(1000),
    suggested_values JSONB NOT NULL,   -- {"Title": "...", "AuthorName": "...", ...}
    source_url VARCHAR(2000),
    confidence NUMERIC(3,2),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending | approved | rejected
    reviewed_at TIMESTAMPTZ,
    resulting_guid UUID,               -- guid созданной/обновлённой книги после approve
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_llm_suggestions_status ON llm_suggestions (org_id, status);
CREATE INDEX idx_llm_suggestions_isbn ON llm_suggestions (isbn);