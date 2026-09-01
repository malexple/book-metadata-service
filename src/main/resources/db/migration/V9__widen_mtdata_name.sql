-- V9__widen_mtdata_name.sql
-- Колонка mt_data.name (денормализованное отображаемое имя записи, используется
-- для листингов/отладки) была объявлена VARCHAR(255) ещё в V2, хотя поле Title
-- в mt_fields официально допускает до 1000 символов (см. V3__book_domain_metadata.sql).
-- Длинные академические/библиотечные заглавия (монографии, сборники, диссертации)
-- легко превышают 255 символов и валили весь batch insert с
-- "value too long for type character varying(255)".
ALTER TABLE mt_data ALTER COLUMN name TYPE VARCHAR(1000);