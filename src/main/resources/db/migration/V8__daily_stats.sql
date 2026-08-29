-- V8__daily_stats.sql
-- Снапшоты для публичных счётчиков на главной странице. Обновляется раз в час
-- через DailyStatsScheduler - никакие "живые" COUNT(*) на публичном эндпоинте не выполняются.

CREATE TABLE daily_stats (
    id BIGSERIAL PRIMARY KEY,
    org_id INTEGER NOT NULL DEFAULT 1 REFERENCES organizations(org_id),
    snapshot_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    total_books BIGINT NOT NULL,
    books_added_today BIGINT NOT NULL,
    authors_verified BIGINT NOT NULL DEFAULT 0,
    requests_today BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_daily_stats_org_snapshot ON daily_stats(org_id, snapshot_at DESC);