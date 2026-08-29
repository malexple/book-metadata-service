package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mcs.metadatabook.entity.ApiKeyScope;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.service.ApiKeyService;
import ru.mcs.metadatabook.service.BookQueryService;

import java.util.List;
import java.util.Map;

/**
 * Анонимный публичный поиск для сайта — единственная точка входа для
 * посетителей без собственного API-ключа.
 *
 * Внутри использует ОДИН общий системный ключ с scope=PUBLIC_READ
 * (book.api.public-api-key), который никогда не передаётся в браузер —
 * фронтенд ходит только на этот эндпоинт, а не напрямую в /api/v1/books.
 *
 * Защита от злоупотреблений — Bucket4j по getRemoteAddr() на url /api/v1/public/*
 * (см. application.yml, bucket4j.filters). Здесь никакой отдельной логики лимитов нет —
 * фильтр Bucket4j срабатывает раньше, чем запрос долетит до этого метода.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public Search", description = "Анонимный поиск книг для сайта (rate-limited)")
public class PublicSearchController {

    private final ApiKeyService apiKeyService;
    private final BookQueryService bookQueryService;

    @Value("${book.api.public-api-key}")
    private String publicApiKey;

    @Operation(summary = "Поиск книги по ISBN или названию (для анонимных посетителей сайта)")
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(required = false) String isbn,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "20") int size) {

        if ((isbn == null || isbn.isBlank()) && (title == null || title.isBlank())) {
            throw new IllegalArgumentException("Provide either isbn or title");
        }

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(publicApiKey, ApiKeyScope.PUBLIC_READ);
        MtObject book = bookQueryService.getBookObject(orgId);
        // Публичному поиску не отдаём служебные/непроверенные поля -
        // SourceStatus/провенанс не нужны стороннему посетителю сайта.
        List<MtField> publicFields = bookQueryService.resolveRequestedFields(
                orgId, book.getObjId(), "Title,Subtitle,AuthorName,Publisher,PublishedYear,ISBN,ISBN13,CoverUrl,Genre,Description");

        List<MtData> records;
        if (isbn != null && !isbn.isBlank()) {
            records = bookQueryService.findByIndexedField(orgId, book.getObjId(), "ISBN", isbn);
        } else {
            records = bookQueryService.findByIndexedFieldContaining(orgId, book.getObjId(), "Title", title);
        }

        return ResponseEntity.ok(records.stream()
                .limit(Math.min(size, 50))
                .map(r -> bookQueryService.toResponseMap(r, publicFields))
                .toList());
    }
}