package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Единственная публичная точка входа для потребителей метаданных - без apiKey,
 * без знания о guid/scope/обогащении как отдельном действии. orgId берётся из
 * конфига (один каталог на публичном сайте).
 *
 * ?fields= - опциональный выбор возвращаемых полей, но ТОЛЬКО из "белого списка"
 * ALLOWED_PUBLIC_FIELDS. Раз этот эндпоинт анонимный и без apiKey, нельзя просто
 * прокидывать имя поля от клиента в BookQueryService как есть - иначе через
 * ?fields=SourceStatus,ExternalLitresId можно было бы вытащить служебные поля,
 * не предназначенные для публичного показа. Неизвестные/непубличные имена полей
 * в запросе просто игнорируются, а не бросают ошибку - чтобы опечатка в fields
 * не роняла весь запрос, а откатывалась на дефолтный набор.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Публичный поиск книг с автообогащением при промахе (rate-limited по IP, без apiKey)")
public class SearchController {

    private static final String DEFAULT_PUBLIC_FIELDS =
            "Title,Subtitle,AuthorName,Publisher,PublishedYear,ISBN,ISBN13,CoverUrl,Genre,Description";

    private static final Set<String> ALLOWED_PUBLIC_FIELDS = Set.of(
            "Title", "Subtitle", "AuthorName", "Publisher", "PublishedYear",
            "ISBN", "ISBN13", "CoverUrl", "Genre", "Description",
            "Pages", "Language", "Series", "SeriesNumber", "Rating", "Format", "AgeRestriction"
    );

    private final BookQueryService bookQueryService;
    private final PublicSourcesEnrichmentService enrichmentService;

    @Value("${book.pivot.default-org-id}")
    private Integer defaultOrgId;

    @Operation(summary = "Поиск по ISBN; если книги нет в базе - подтягиваем из внешних источников")
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<List<Map<String, Object>>> byIsbn(
            @PathVariable String isbn,
            @Parameter(example = "Title,AuthorName,ISBN") @RequestParam(required = false) String fields) {

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

        return respond(book, records, fields);
    }

    @Operation(summary = "Поиск по названию; если ничего не нашлось - подтягиваем из внешних источников")
    @GetMapping("/title")
    public ResponseEntity<List<Map<String, Object>>> byTitle(
            @RequestParam String q,
            @Parameter(example = "Title,AuthorName,PublishedYear") @RequestParam(required = false) String fields) {

        MtObject book = bookQueryService.getBookObject(defaultOrgId);

        List<MtData> records = bookQueryService.findByIndexedFieldContaining(defaultOrgId, book.getObjId(), "Title", q);
        if (records.isEmpty()) {
            records = enrichmentService.enrichByTitleMultiple(defaultOrgId, q, false);
        }

        return respond(book, records, fields);
    }

    @Operation(summary = "Универсальный поиск - сам определяет, похож ли запрос на ISBN или на название")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam String q,
            @Parameter(example = "Title,ISBN,CoverUrl") @RequestParam(required = false) String fields) {

        String candidate = IsbnValidator.normalize(q);
        if (IsbnValidator.isValid(candidate)) {
            return byIsbn(candidate, fields);
        }
        return byTitle(q.trim(), fields);
    }

    private ResponseEntity<List<Map<String, Object>>> respond(MtObject book, List<MtData> records, String requestedFields) {
        String fieldsToUse = filterToAllowedFields(requestedFields);
        List<MtField> publicFields = bookQueryService.resolveRequestedFields(defaultOrgId, book.getObjId(), fieldsToUse);

        List<Map<String, Object>> body = new ArrayList<>();
        for (MtData r : records) {
            body.add(bookQueryService.toResponseMap(r, publicFields));
        }
        return ResponseEntity.ok(body);
    }

    private String filterToAllowedFields(String requestedFields) {
        if (requestedFields == null || requestedFields.isBlank()) {
            return DEFAULT_PUBLIC_FIELDS;
        }

        Set<String> filtered = new LinkedHashSet<>();
        for (String f : requestedFields.split(",")) {
            String trimmed = f.trim();
            if (ALLOWED_PUBLIC_FIELDS.contains(trimmed)) {
                filtered.add(trimmed);
            }
        }

        return filtered.isEmpty() ? DEFAULT_PUBLIC_FIELDS : String.join(",", filtered);
    }
}