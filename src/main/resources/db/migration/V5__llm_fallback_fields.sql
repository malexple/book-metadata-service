INSERT INTO mt_fields (org_id, obj_id, field_name, label, data_type, field_num, is_indexed, is_unique, is_required, length)
SELECT
    o.org_id,
    o.obj_id,
    'SourceUrl',
    'Ссылка на источник (LLM fallback)',
    'url',
    (SELECT COALESCE(MAX(mf.field_num), -1) + 1 FROM mt_fields mf WHERE mf.org_id = o.org_id AND mf.obj_id = o.obj_id),
    FALSE,
    FALSE,
    FALSE,
    2000
FROM mt_objects o
WHERE o.org_id = 1 AND o.obj_name = 'Book'
  AND NOT EXISTS (
      SELECT 1 FROM mt_fields mf
      WHERE mf.org_id = o.org_id AND mf.obj_id = o.obj_id AND mf.field_name = 'SourceUrl'
  );