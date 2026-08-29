package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.mcs.metadatabook.dto.CreateBookRequest;
import ru.mcs.metadatabook.entity.ApiKeyScope;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.service.ApiKeyService;
import ru.mcs.metadatabook.service.BookQueryService;
import ru.mcs.metadatabook.service.BookWriteService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Единый динамический API, который пользователь встраивает в свой сервис
 * (UI-конструктор не создаёт статичные профили, а просто помогает собрать
 * корректный запрос сюда).
 *
 * Примеры:
 *   GET  /api/v1/books?apiKey=...&fields=title,isbn,author_name
 *   GET  /api/v1/books/search?apiKey=...&field=ISBN&value=9785171234567&fields=title,isbn
 *   GET  /api/v1/books/{guid}?apiKey=...&fields=title,description
 *   POST /api/v1/books?apiKey=...   (создание книги вручную)
 */
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "Публичный динамический API книг с выбором полей")
public class BookApiController {

    private final ApiKeyService apiKeyService;
    private final BookQueryService bookQueryService;
    private final BookWriteService bookWriteService;

    @Operation(summary = "Список книг с выбором полей", description = "fields=title,isbn,author_name — через запятую, без пробелов")
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listBooks(
            @RequestParam String apiKey,
            @RequestParam(required = false) String fields,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey, ApiKeyScope.PUBLIC_READ);
        MtObject book = bookQueryService.getBookObject(orgId);
        List<MtField> requested = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), fields);

        List<MtData> records = bookQueryService.findAll(orgId, book.getObjId(), page, Math.min(size, 200));
        List<Map<String, Object>> response = records.stream()
                .map(r -> bookQueryService.toResponseMap(r, requested))
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Поиск по индексированному полю", description = "Например ?field=ISBN&value=9785171234567")
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam String apiKey,
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam(required = false) String fields) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey, ApiKeyScope.PUBLIC_READ);
        MtObject book = bookQueryService.getBookObject(orgId);
        List<MtField> requested = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), fields);

        List<MtData> records = bookQueryService.findByIndexedField(orgId, book.getObjId(), field, value);
        List<Map<String, Object>> response = records.stream()
                .map(r -> bookQueryService.toResponseMap(r, requested))
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Получить книгу по GUID")
    @GetMapping("/{guid}")
    public ResponseEntity<Map<String, Object>> getBook(
            @RequestParam String apiKey,
            @PathVariable UUID guid,
            @RequestParam(required = false) String fields) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey, ApiKeyScope.PUBLIC_READ);
        MtObject book = bookQueryService.getBookObject(orgId);
        List<MtField> requested = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), fields);

        MtData data = bookQueryService.findByGuid(orgId, guid);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(bookQueryService.toResponseMap(data, requested));
    }

    @Operation(summary = "Создать книгу вручную")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createBook(
            @RequestParam String apiKey,
            @Valid @RequestBody CreateBookRequest request) {

        Integer orgId = apiKeyService.resolveOrgIdOrThrow(apiKey, ApiKeyScope.PARTNER_WRITE);
        MtData saved = bookWriteService.createBook(orgId, request.getValues(), request.getSource(), request.getConfidence());

        MtObject book = bookQueryService.getBookObject(orgId);
        List<MtField> allFields = bookQueryService.resolveRequestedFields(orgId, book.getObjId(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(bookQueryService.toResponseMap(saved, allFields));
    }
}