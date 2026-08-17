-- V3__book_domain_metadata.sql
-- Наполнение mt_objects/mt_fields метаданными книжного каталога.

INSERT INTO mt_objects (org_id, obj_name, label, plural_label, description, is_custom, is_active)
VALUES
    (1, 'Book',     'Книга',   'Книги',   'Печатные и электронные книги', TRUE, TRUE),
    (1, 'Author',   'Автор',   'Авторы',  'Авторы произведений',          TRUE, TRUE),
    (1, 'Magazine', 'Журнал',  'Журналы', 'Периодические издания',        TRUE, TRUE);

-- Book
INSERT INTO mt_fields (org_id, obj_id, field_name, label, data_type, field_num,
                        is_indexed, is_unique, is_required, length)
SELECT o.org_id, o.obj_id, f.field_name, f.label, f.data_type, f.field_num,
       f.is_indexed, f.is_unique, f.is_required, f.length
FROM mt_objects o
CROSS JOIN (VALUES
    ('ISBN',             'ISBN',                  'text',     0,  TRUE,  TRUE,  TRUE,  20),
    ('ISBN13',           'ISBN-13',               'text',     1,  TRUE,  TRUE,  FALSE, 20),
    ('Title',            'Название',              'text',     2,  TRUE,  FALSE, TRUE,  1000),
    ('Subtitle',         'Подзаголовок',          'text',     3,  FALSE, FALSE, FALSE, 1000),
    ('AuthorId',         'Автор (ссылка)',        'lookup',   4,  TRUE,  FALSE, FALSE, NULL),
    ('AuthorName',       'Автор (текст)',         'text',     5,  TRUE,  FALSE, FALSE, 500),
    ('Publisher',        'Издательство',          'text',     6,  TRUE,  FALSE, FALSE, 500),
    ('PublishedYear',    'Год издания',           'number',   7,  TRUE,  FALSE, FALSE, NULL),
    ('Pages',            'Кол-во страниц',        'number',   8,  FALSE, FALSE, FALSE, NULL),
    ('Language',         'Язык',                  'picklist', 9,  TRUE,  FALSE, FALSE, 50),
    ('Genre',            'Жанр',                  'picklist', 10, TRUE,  FALSE, FALSE, 200),
    ('Series',           'Серия',                 'text',     11, TRUE,  FALSE, FALSE, 500),
    ('SeriesNumber',     'Номер в серии',         'number',   12, FALSE, FALSE, FALSE, NULL),
    ('Description',      'Описание',              'text',     13, FALSE, FALSE, FALSE, 4000),
    ('CoverUrl',         'Обложка (URL)',         'url',      14, FALSE, FALSE, FALSE, 2000),
    ('Rating',           'Рейтинг',               'number',   15, FALSE, FALSE, FALSE, NULL),
    ('AgeRestriction',   'Возрастное огр.',       'text',     16, FALSE, FALSE, FALSE, 10),
    ('Format',           'Формат (pdf/djvu/fb2)', 'picklist', 17, TRUE,  FALSE, FALSE, 20),
    ('SourceStatus',     'Статус метаданных',     'picklist', 18, TRUE,  FALSE, FALSE, 30),
    ('ExternalLitresId', 'ID в Litres',           'text',     19, TRUE,  FALSE, FALSE, 100),
    ('ExternalOzonId',   'ID в Ozon',             'text',     20, TRUE,  FALSE, FALSE, 100)
) AS f(field_name, label, data_type, field_num, is_indexed, is_unique, is_required, length)
WHERE o.org_id = 1 AND o.obj_name = 'Book';

-- Author
INSERT INTO mt_fields (org_id, obj_id, field_name, label, data_type, field_num,
                        is_indexed, is_unique, is_required, length)
SELECT o.org_id, o.obj_id, f.field_name, f.label, f.data_type, f.field_num,
       f.is_indexed, f.is_unique, f.is_required, f.length
FROM mt_objects o
CROSS JOIN (VALUES
    ('FullName',  'Полное имя',   'text',   0, TRUE,  FALSE, TRUE,  500),
    ('BirthYear', 'Год рождения', 'number', 1, FALSE, FALSE, FALSE, NULL),
    ('Country',   'Страна',       'text',   2, FALSE, FALSE, FALSE, 200),
    ('Bio',       'Биография',    'text',   3, FALSE, FALSE, FALSE, 4000)
) AS f(field_name, label, data_type, field_num, is_indexed, is_unique, is_required, length)
WHERE o.org_id = 1 AND o.obj_name = 'Author';

-- Magazine (пример другого набора полей)
INSERT INTO mt_fields (org_id, obj_id, field_name, label, data_type, field_num,
                        is_indexed, is_unique, is_required, length)
SELECT o.org_id, o.obj_id, f.field_name, f.label, f.data_type, f.field_num,
       f.is_indexed, f.is_unique, f.is_required, f.length
FROM mt_objects o
CROSS JOIN (VALUES
    ('Title',       'Название',      'text',     0, TRUE,  FALSE, TRUE,  500),
    ('IssueNumber', 'Номер выпуска', 'number',   1, TRUE,  FALSE, TRUE,  NULL),
    ('IssueDate',   'Дата выпуска',  'date',     2, TRUE,  FALSE, FALSE, NULL),
    ('Publisher',   'Издательство',  'text',     3, TRUE,  FALSE, FALSE, 500),
    ('Periodicity', 'Периодичность', 'picklist', 4, FALSE, FALSE, FALSE, 50),
    ('ISSN',        'ISSN',          'text',     5, TRUE,  TRUE,  FALSE, 20)
) AS f(field_name, label, data_type, field_num, is_indexed, is_unique, is_required, length)
WHERE o.org_id = 1 AND o.obj_name = 'Magazine';
