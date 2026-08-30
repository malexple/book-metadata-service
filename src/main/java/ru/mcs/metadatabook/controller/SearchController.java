package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.service.BookQueryService;
import ru.mcs.metadatabook.service.PublicSourcesEnrichmentService;
import ru.mcs.metadatabook.util.IsbnValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Единственная публичная точка входа для потребителей метаданных (плагины
 * Obsidian/Calibre и т.п.) — без apiKey, без знания о guid/scope/обогащении
 * как отдельном действии. orgId берётся из конфига (один каталог на публичном сайте).
 *
 * ISBN всегда проходит через IsbnValidator.normalize()+isValid() ДО любого
 * обращения к БД или внешним источникам — это одновременно:
 *  - защита от дублей в mt_data (одна книга под разными форматами ISBN
 *    больше не создаёт две записи, см. историю с "978-5-389-14385-2" vs
 *    "9785389143852");
 *  - защита от лишних внешних HTTP-запросов на заведомо невалидный ввод;
 *  - понятная ошибка 400 для потребителя вместо тихого "ничего не нашли".
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Публичный поиск книг с автообогащением при промахе (rate-limited по IP, без apiKey)")
public class SearchController {

    private static final String PUBLIC_FIELDS =
            "Title,Subtitle,AuthorName,Publisher,PublishedYear,ISBN,ISBN13,CoverUrl,Genre,Description";

    private final BookQueryService bookQueryService;
    private final PublicSourcesEnrichmentService enrichmentService;

    @Value("${book.pivot.default-org-id}")
    private Integer defaultOrgId;

    @Operation(summary = "Поиск по ISBN; если книги нет в базе - подтягиваем из внешних источников")
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<List<Map<String, Object>>> byIsbn(@PathVariable String isbn) {
        String normalized = IsbnValidator.normalize(isbn);
        if (!IsbnValidator.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid ISBN: '" + isbn + "' (failed checksum validation)");
        }

        MtObject book = bookQueryService.getBookObject(defaultOrgId);

        List<MtData> records = bookQueryService.findByIndexedField(defaultOrgId, book.getObjId(), "ISBN", normalized);
        if (records.isEmpty()) {
            MtData enriched = enrichmentService.enrichByIsbn(defaultOrgId, normalized, false);
            records = enriched != null ? List.of(enriched) : List.of();
        }

        return respond(book, records);
    }

    @Operation(summary = "Поиск по названию; если ничего не нашлось - подтягиваем из внешних источников")
    @GetMapping("/title")
    public ResponseEntity<List<Map<String, Object>>> byTitle(@RequestParam String q) {
        MtObject book = bookQueryService.getBookObject(defaultOrgId);

        List<MtData> records = bookQueryService.findByIndexedFieldContaining(defaultOrgId, book.getObjId(), "Title", q);
        if (records.isEmpty()) {
            records = enrichmentService.enrichByTitleMultiple(defaultOrgId, q, false);
        }

        return respond(book, records);
    }

    @Operation(summary = "Универсальный поиск - сам определяет, похож ли запрос на ISBN или на название")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam String q) {
        String candidate = IsbnValidator.normalize(q);
        if (IsbnValidator.isValid(candidate)) {
            return byIsbn(candidate);
        }
        return byTitle(q.trim());
    }

    private ResponseEntity<List<Map<String, Object>>> respond(MtObject book, List<MtData> records) {
        List<MtField> publicFields = bookQueryService.resolveRequestedFields(defaultOrgId, book.getObjId(), PUBLIC_FIELDS);
        List<Map<String, Object>> body = new ArrayList<>();
        for (MtData r : records) {
            body.add(bookQueryService.toResponseMap(r, publicFields));
        }
        return ResponseEntity.ok(body);
    }
}