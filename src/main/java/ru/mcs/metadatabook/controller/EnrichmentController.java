package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.service.ApiKeyService;
import ru.mcs.metadatabook.service.BookQueryService;
import ru.mcs.metadatabook.service.PublicSourcesEnrichmentService;

import java.util.Map;

/**
 * Ручной запуск обогащения из внешних источников.
 *
 * /public/by-isbn  — Open Library -> Google Books -> FantLab -> LibGen -> LLM fallback.
 *                     Основной путь, когда ISBN известен.
 *
 * /public/by-title — тот же набор источников (кроме LibGen — его API не умеет
 *                     искать по названию), для изданий БЕЗ ISBN — старые советские
 *                     книги, малотиражные технические справочники и т.п.
 *                     Точность ниже: совпадение по названию не гарантирует то самое
 *                     издание, особенно для книг с общими/родовыми названиями —
 *                     рекомендуется сверять AuthorName/PublishedYear вручную
 *                     перед тем, как доверять результату.
 */
@RestController
@RequestMapping("/api/v1/enrichment")
@RequiredArgsConstructor
@Tag(name = "Enrichment", description = "Обогащение метаданных из открытых источников и Litres")
public class EnrichmentController {

    private final ApiKeyService apiKeyService;
    private final PublicSourcesEnrichmentService publicSourcesEnrichmentService;
    private final BookQueryService bookQueryService;

    @Operation(summary = "Найти книгу по ISBN в открытых источниках (Open Library -> Google Books -> FantLab -> LibGen -> LLM fallback)")
    @PostMapping("/public/by-isbn")
    public ResponseEntity<Map<String, Object>> enrichPublicByIsbn(
            @RequestParam String apiKey,
            @RequestParam String isbn) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        MtData result = publicSourcesEnrichmentService.enrichByIsbn(orgId, isbn);
        return respond(orgId, result);
    }

    @Operation(summary = "Найти книгу по названию, когда ISBN неизвестен (старые издания, книги без ISBN)")
    @PostMapping("/public/by-title")
    public ResponseEntity<Map<String, Object>> enrichPublicByTitle(
            @RequestParam String apiKey,
            @RequestParam String title) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        MtData result = publicSourcesEnrichmentService.enrichByTitle(orgId, title);
        return respond(orgId, result);
    }

    private ResponseEntity<Map<String, Object>> respond(Integer orgId, MtData result) {
        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "Книга не найдена, добавлена/обновлена в очереди unresolved_lookup"
            ));
        }
        var book = bookQueryService.getBookObject(orgId);
        var fields = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), null);
        return ResponseEntity.ok(bookQueryService.toResponseMap(result, fields));
    }
}