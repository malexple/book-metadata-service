package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mcs.metadatabook.entity.LlmSuggestion;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.service.ApiKeyService;
import ru.mcs.metadatabook.service.BookQueryService;
import ru.mcs.metadatabook.service.PublicSourcesEnrichmentService;

import java.util.List;
import java.util.Map;

/**
 * Ручной запуск обогащения из внешних источников + модерация LLM-предложений.
 *
 * /public/by-isbn, /public/by-title — структурированные источники пишутся в
 *   mt_data сразу; если срабатывает LLM fallback — результат уходит в
 *   llm_suggestions и НЕ попадает в mt_data автоматически (ответ будет
 *   found=false, "добавлена/обновлена в очереди", как и для unresolved_lookup).
 *
 * /llm-suggestions        — список предложений на модерацию.
 * /llm-suggestions/{id}/approve — принять предложение, записать в mt_data.
 * /llm-suggestions/{id}/reject  — отклонить предложение.
 */
@RestController
@RequestMapping("/api/v1/enrichment")
@RequiredArgsConstructor
@Tag(name = "Enrichment", description = "Обогащение метаданных из открытых источников, LLM fallback и модерация предложений")
public class EnrichmentController {

    private final ApiKeyService apiKeyService;
    private final PublicSourcesEnrichmentService publicSourcesEnrichmentService;
    private final BookQueryService bookQueryService;

    @Operation(summary = "Найти книгу по ISBN (Open Library -> Google Books -> FantLab -> LibGen -> LLM fallback в очередь)")
    @PostMapping("/public/by-isbn")
    public ResponseEntity<Map<String, Object>> enrichPublicByIsbn(
            @RequestParam String apiKey,
            @RequestParam String isbn) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        MtData result = publicSourcesEnrichmentService.enrichByIsbn(orgId, isbn);
        return respond(orgId, result);
    }

    @Operation(summary = "Найти книгу по названию, когда ISBN неизвестен (LLM fallback тоже уходит в очередь на модерацию)")
    @PostMapping("/public/by-title")
    public ResponseEntity<Map<String, Object>> enrichPublicByTitle(
            @RequestParam String apiKey,
            @RequestParam String title) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        MtData result = publicSourcesEnrichmentService.enrichByTitle(orgId, title);
        return respond(orgId, result);
    }

    @Operation(summary = "Список предложений от LLM fallback, ожидающих модерации (по умолчанию status=pending)")
    @GetMapping("/llm-suggestions")
    public ResponseEntity<List<LlmSuggestion>> listSuggestions(
            @RequestParam String apiKey,
            @RequestParam(defaultValue = "pending") String status) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        return ResponseEntity.ok(publicSourcesEnrichmentService.listSuggestions(orgId, status));
    }

    @Operation(summary = "Принять предложение LLM — записать данные в mt_data")
    @PostMapping("/llm-suggestions/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveSuggestion(
            @RequestParam String apiKey,
            @PathVariable Long id) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        MtData result = publicSourcesEnrichmentService.approveSuggestion(orgId, id);

        var book = bookQueryService.getBookObject(orgId);
        var fields = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), null);
        return ResponseEntity.ok(bookQueryService.toResponseMap(result, fields));
    }

    @Operation(summary = "Отклонить предложение LLM — без записи в mt_data")
    @PostMapping("/llm-suggestions/{id}/reject")
    public ResponseEntity<Map<String, Object>> rejectSuggestion(
            @RequestParam String apiKey,
            @PathVariable Long id) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey);
        publicSourcesEnrichmentService.rejectSuggestion(orgId, id);
        return ResponseEntity.ok(Map.of("rejected", true, "id", id));
    }

    private ResponseEntity<Map<String, Object>> respond(Integer orgId, MtData result) {
        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "Книга не найдена структурированными источниками. " +
                            "Если сработал LLM fallback — предложение в /llm-suggestions на модерацию, " +
                            "иначе — в очереди unresolved_lookup."
            ));
        }
        var book = bookQueryService.getBookObject(orgId);
        var fields = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), null);
        return ResponseEntity.ok(bookQueryService.toResponseMap(result, fields));
    }
}