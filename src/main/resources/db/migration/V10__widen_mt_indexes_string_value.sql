-- string_value был VARCHAR(255) с V2, хотя mt_fields.length для Title/AuthorName/
-- Publisher/Series заявляет 500-1000. Та же проблема, что чинили в V9 для mt_data.name.
ALTER TABLE mt_indexes ALTER COLUMN string_value TYPE VARCHAR(1000);