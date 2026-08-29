package ru.mcs.metadatabook.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.client.*;
import ru.mcs.metadatabook.entity.LlmSuggestion;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.UnresolvedLookup;
import ru.mcs.metadatabook.repository.LlmSuggestionRepository;
import ru.mcs.metadatabook.repository.UnresolvedLookupRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Обогащение метаданных из открытых источников.
 *
 * Порядок для enrichByIsbn:  Open Library -> Google Books -> FantLab -> LibGen
 *   (если включён) -> LLM fallback (если включён) -> unresolved_lookup.
 * Порядок для enrichByTitle: Open Library -> Google Books -> FantLab ->
 *   LLM fallback (если включён) -> unresolved_lookup. (LibGen не участвует —
 *   его API ищет только по ISBN.)
 *
 * ВАЖНО: структурированные источники (Open Library/Google Books/FantLab/LibGen)
 * по-прежнему пишутся в mt_data сразу — они возвращают точные, проверяемые данные.
 * Результаты LLM fallback НЕ пишутся в mt_data напрямую — они складываются в
 * таблицу llm_suggestions на ручную модерацию (см. LlmSuggestion,
 * approveSuggestion/rejectSuggestion). Это дополнительный уровень защиты поверх
 * верификации внутри LlmFallbackClient: даже прошедшие верификацию данные
 * генеративной модели — низкой достоверности по своей природе.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicSourcesEnrichmentService {

    private final OpenLibraryClient openLibraryClient;
    private final GoogleBooksClient googleBooksClient;
    private final FantLabClient fantLabClient;
    private final LibGenClient libGenClient;
    private final LlmFallbackClient llmFallbackClient;
    private final SiglaClient siglaClient;
    private final BookQueryService bookQueryService;
    private final BookWriteService bookWriteService;
    private final UnresolvedLookupRepository unresolvedLookupRepository;
    private final LlmSuggestionRepository llmSuggestionRepository;

    @Value("${llm.fallback.enabled:false}")
    private boolean llmFallbackEnabled;

    @Value("${llm.fallback.confidence:0.3}")
    private double llmFallbackConfidence;

    @Transactional
    public MtData enrichByIsbn(Integer orgId, String isbn) {
        List<String> attempted = new ArrayList<>();

        JsonNode olBook = openLibraryClient.findByIsbn(isbn);
        attempted.add("openlibrary");
        if (olBook != null) {
            return persist(orgId, "ISBN", isbn, mapOpenLibrary(olBook, isbn), "openlibrary", 0.7);
        }

        JsonNode gbVolume = googleBooksClient.findByIsbn(isbn);
        attempted.add("googlebooks");
        if (gbVolume != null) {
            return persist(orgId, "ISBN", isbn, mapGoogleBooks(gbVolume, isbn), "googlebooks", 0.7);
        }

        JsonNode flEdition = fantLabClient.findByIsbn(isbn);
        attempted.add("fantlab");
        if (flEdition != null) {
            return persist(orgId, "ISBN", isbn, mapFantLab(flEdition, isbn), "fantlab", 0.65);
        }

        JsonNode lgEdition = libGenClient.findByIsbn(isbn);
        attempted.add("libgen");
        if (lgEdition != null) {
            return persist(orgId, "ISBN", isbn, mapLibGen(lgEdition, isbn), "libgen", 0.4);
        }

        if (llmFallbackEnabled) {
            JsonNode llmResult = llmFallbackClient.findByIsbn(isbn);
            attempted.add("llm_fallback");
            if (llmResult != null) {
                LlmSuggestion suggestion = saveSuggestion(orgId, isbn, null, llmResult);
                log.info("ISBN {} восстановлен через LLM fallback -> llm_suggestions#{} (требует ручной модерации)",
                        isbn, suggestion.getId());
                return null; // не пишем в mt_data — ждём approve
            }
        }

        saveUnresolved(orgId, isbn, null, null, attempted);
        log.info("ISBN {} not found in any source (attempted: {}), saved to unresolved_lookup", isbn, attempted);
        return null;
    }

    /**
     * Обогащение по названию — для изданий без ISBN (старые книги и т.п.).
     */
    @Transactional
    public MtData enrichByTitle(Integer orgId, String title) {
        List<String> attempted = new ArrayList<>();

        JsonNode olDoc = openLibraryClient.findByTitle(title);
        attempted.add("openlibrary");
        if (olDoc != null) {
            return persist(orgId, "Title", title, mapOpenLibrarySearch(olDoc), "openlibrary", 0.5);
        }

        JsonNode gbVolume = googleBooksClient.searchByTitle(title, 1);
        attempted.add("googlebooks");
        if (gbVolume != null) {
            return persist(orgId, "Title", title, mapGoogleBooks(gbVolume, null), "googlebooks", 0.5);
        }

        JsonNode flEdition = fantLabClient.findByTitle(title);
        attempted.add("fantlab");
        if (flEdition != null) {
            return persist(orgId, "Title", title, mapFantLab(flEdition, null), "fantlab", 0.5);
        }

        if (llmFallbackEnabled) {
            JsonNode llmResult = llmFallbackClient.findByTitle(title);
            attempted.add("llm_fallback");
            if (llmResult != null) {
                LlmSuggestion suggestion = saveSuggestion(orgId, null, title, llmResult);
                log.info("Книга '{}' восстановлена через LLM fallback -> llm_suggestions#{} (требует ручной модерации)",
                        title, suggestion.getId());
                return null; // не пишем в mt_data — ждём approve
            }
        }

        saveUnresolved(orgId, null, title, null, attempted);
        log.info("Book '{}' not found by title in any source (attempted: {}), saved to unresolved_lookup", title, attempted);
        return null;
    }

    // ---------- Модерация LLM-предложений ----------

    public List<LlmSuggestion> listSuggestions(Integer orgId, String status) {
        return llmSuggestionRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, status);
    }

    /** Принимает предложение LLM и только теперь пишет данные в mt_data. */
    @Transactional
    public MtData approveSuggestion(Integer orgId, Long suggestionId) {
        LlmSuggestion suggestion = llmSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));

        if (!"pending".equals(suggestion.getStatus())) {
            throw new IllegalArgumentException("Suggestion already reviewed: " + suggestionId + " (status=" + suggestion.getStatus() + ")");
        }

        String dedupeField = suggestion.getIsbn() != null ? "ISBN" : "Title";
        String dedupeValue = suggestion.getIsbn() != null ? suggestion.getIsbn() : suggestion.getQueryTitle();

        MtData saved = persist(orgId, dedupeField, dedupeValue, suggestion.getSuggestedValues(),
                "llm_fallback", llmFallbackConfidence);

        suggestion.setStatus("approved");
        suggestion.setReviewedAt(OffsetDateTime.now());
        suggestion.setResultingGuid(saved.getGuid());
        llmSuggestionRepository.save(suggestion);

        return saved;
    }

    @Transactional
    public void rejectSuggestion(Integer orgId, Long suggestionId) {
        LlmSuggestion suggestion = llmSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found: " + suggestionId));

        if (!"pending".equals(suggestion.getStatus())) {
            throw new IllegalArgumentException("Suggestion already reviewed: " + suggestionId + " (status=" + suggestion.getStatus() + ")");
        }

        suggestion.setStatus("rejected");
        suggestion.setReviewedAt(OffsetDateTime.now());
        llmSuggestionRepository.save(suggestion);
    }

    private LlmSuggestion saveSuggestion(Integer orgId, String isbn, String queryTitle, JsonNode llmResult) {
        Map<String, String> values = mapLlmFallback(llmResult, isbn);
        String sourceUrl = textOrNull(llmResult, "source_url");

        LlmSuggestion suggestion = LlmSuggestion.builder()
                .orgId(orgId)
                .isbn(isbn)
                .queryTitle(queryTitle)
                .suggestedValues(values)
                .sourceUrl(sourceUrl)
                .confidence(BigDecimal.valueOf(llmFallbackConfidence))
                .status("pending")
                .build();

        return llmSuggestionRepository.save(suggestion);
    }

    // ---------- Персист структурированных источников ----------

    private MtData persist(Integer orgId, String dedupeFieldName, String dedupeValue,
                           Map<String, String> values, String source, double confidence) {
        var bookObject = bookQueryService.getBookObject(orgId);
        var existing = bookQueryService.findByIndexedField(orgId, bookObject.getObjId(), dedupeFieldName, dedupeValue);

        if (!existing.isEmpty()) {
            return bookWriteService.enrichBook(orgId, existing.get(0).getGuid(), values, source, confidence);
        }
        return bookWriteService.createBook(orgId, values, source, confidence);
    }

    private Map<String, String> mapOpenLibrary(JsonNode book, String isbn) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", isbn);
        putIfPresent(values, "Title", textOrNull(book, "title"));
        putIfPresent(values, "Subtitle", textOrNull(book, "subtitle"));
        putIfPresent(values, "Pages", textOrNull(book, "number_of_pages"));

        JsonNode authors = book.get("authors");
        if (authors != null && authors.isArray()) {
            String names = StreamSupport.stream(authors.spliterator(), false)
                    .map(a -> textOrNull(a, "name"))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.joining(", "));
            putIfPresent(values, "AuthorName", names);
        }

        JsonNode publishers = book.get("publishers");
        if (publishers != null && publishers.isArray() && !publishers.isEmpty()) {
            putIfPresent(values, "Publisher", textOrNull(publishers.get(0), "name"));
        }

        putIfPresent(values, "PublishedYear", extractYear(textOrNull(book, "publish_date")));

        JsonNode cover = book.get("cover");
        if (cover != null) {
            String coverUrl = textOrNull(cover, "large");
            if (coverUrl == null) coverUrl = textOrNull(cover, "medium");
            putIfPresent(values, "CoverUrl", coverUrl);
        }

        values.put("SourceStatus", "enriched_openlibrary");
        return values;
    }

    private Map<String, String> mapOpenLibrarySearch(JsonNode doc) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "Title", textOrNull(doc, "title"));

        JsonNode authorNames = doc.get("author_name");
        if (authorNames != null && authorNames.isArray()) {
            String names = StreamSupport.stream(authorNames.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
            putIfPresent(values, "AuthorName", names);
        }

        JsonNode publishers = doc.get("publisher");
        if (publishers != null && publishers.isArray() && !publishers.isEmpty()) {
            putIfPresent(values, "Publisher", publishers.get(0).asText(null));
        }

        if (doc.has("first_publish_year") && !doc.get("first_publish_year").isNull()) {
            putIfPresent(values, "PublishedYear", String.valueOf(doc.get("first_publish_year").asInt()));
        }

        JsonNode isbns = doc.get("isbn");
        if (isbns != null && isbns.isArray() && !isbns.isEmpty()) {
            putIfPresent(values, "ISBN", isbns.get(0).asText(null));
        }

        if (doc.has("cover_i") && !doc.get("cover_i").isNull()) {
            putIfPresent(values, "CoverUrl", "https://covers.openlibrary.org/b/id/" + doc.get("cover_i").asText() + "-L.jpg");
        }

        values.put("SourceStatus", "enriched_openlibrary_by_title");
        return values;
    }

    private Map<String, String> mapGoogleBooks(JsonNode volumeInfo, String isbn) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", isbn);
        putIfPresent(values, "Title", textOrNull(volumeInfo, "title"));
        putIfPresent(values, "Subtitle", textOrNull(volumeInfo, "subtitle"));
        putIfPresent(values, "Publisher", textOrNull(volumeInfo, "publisher"));
        putIfPresent(values, "PublishedYear", extractYear(textOrNull(volumeInfo, "publishedDate")));
        putIfPresent(values, "Pages", textOrNull(volumeInfo, "pageCount"));
        putIfPresent(values, "Description", textOrNull(volumeInfo, "description"));
        putIfPresent(values, "Language", textOrNull(volumeInfo, "language"));

        JsonNode authors = volumeInfo.get("authors");
        if (authors != null && authors.isArray()) {
            String names = StreamSupport.stream(authors.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
            putIfPresent(values, "AuthorName", names);
        }

        JsonNode categories = volumeInfo.get("categories");
        if (categories != null && categories.isArray()) {
            String genres = StreamSupport.stream(categories.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
            putIfPresent(values, "Genre", genres);
        }

        JsonNode imageLinks = volumeInfo.get("imageLinks");
        if (imageLinks != null) {
            putIfPresent(values, "CoverUrl", textOrNull(imageLinks, "thumbnail"));
        }

        values.put("SourceStatus", isbn != null ? "enriched_googlebooks" : "enriched_googlebooks_by_title");
        return values;
    }

    private Map<String, String> mapFantLab(JsonNode edition, String isbn) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", isbn);
        putIfPresent(values, "Title", FantLabClient.stripBbCode(textOrNull(edition, "name")));
        putIfPresent(values, "AuthorName", FantLabClient.stripBbCode(textOrNull(edition, "autors")));
        putIfPresent(values, "Publisher", FantLabClient.stripBbCode(textOrNull(edition, "publisher")));
        putIfPresent(values, "Series", FantLabClient.stripBbCode(textOrNull(edition, "series")));
        putIfPresent(values, "PublishedYear", textOrNull(edition, "year"));
        putIfPresent(values, "Genre", "Фантастика");

        values.put("SourceStatus", isbn != null ? "enriched_fantlab" : "enriched_fantlab_by_title");
        return values;
    }

    private Map<String, String> mapLibGen(JsonNode edition, String isbn) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", isbn);
        putIfPresent(values, "Title", textOrNull(edition, "title"));
        putIfPresent(values, "AuthorName", textOrNull(edition, "author"));
        putIfPresent(values, "Publisher", textOrNull(edition, "publisher"));
        putIfPresent(values, "Pages", textOrNull(edition, "pages"));
        putIfPresent(values, "PublishedYear", extractYear(textOrNull(edition, "year")));
        putIfPresent(values, "CoverUrl", textOrNull(edition, "cover_url"));

        values.put("SourceStatus", "enriched_libgen");
        return values;
    }

    private Map<String, String> mapLlmFallback(JsonNode result, String isbn) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", isbn != null ? isbn : textOrNull(result, "isbn"));
        putIfPresent(values, "Title", textOrNull(result, "title"));
        putIfPresent(values, "AuthorName", textOrNull(result, "author"));
        putIfPresent(values, "Publisher", textOrNull(result, "publisher"));
        putIfPresent(values, "PublishedYear", textOrNull(result, "year"));

        String sourceUrl = textOrNull(result, "source_url");
        putIfPresent(values, "SourceUrl", sourceUrl);

        values.put("SourceStatus", "enriched_llm_fallback_needs_review");
        return values;
    }

    private void saveUnresolved(Integer orgId, String isbn, String title, String authorName, List<String> attempted) {
        UnresolvedLookup existing = isbn != null
                ? unresolvedLookupRepository.findByOrgIdAndIsbn(orgId, isbn).orElse(null)
                : null;

        if (existing != null) {
            List<String> sources = new ArrayList<>(java.util.Arrays.asList(existing.getAttemptedSources()));
            for (String s : attempted) {
                if (!sources.contains(s)) sources.add(s);
            }
            existing.setAttemptedSources(sources.toArray(new String[0]));
            existing.setUpdatedAt(OffsetDateTime.now());
            unresolvedLookupRepository.save(existing);
            return;
        }

        UnresolvedLookup lookup = UnresolvedLookup.builder()
                .orgId(orgId)
                .isbn(isbn)
                .title(title)
                .authorName(authorName)
                .attemptedSources(attempted.toArray(new String[0]))
                .status("pending")
                .rawContext(new HashMap<>())
                .build();
        unresolvedLookupRepository.save(lookup);
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    private String extractYear(String dateStr) {
        if (dateStr == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})").matcher(dateStr);
        return m.find() ? m.group(1) : null;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    /**
     * Обогащение по названию с возвратом ВСЕХ найденных кандидатов, а не одного
     * "угаданного". Для родовых заглавий (словари, справочники и т.п.) один и тот же
     * запрос закономерно совпадает с десятками разных изданий — сервис не имеет
     * достаточно контекста, чтобы выбрать среди них правильное (это может сделать
     * только вызывающее приложение или человек, у которого перед глазами конкретный
     * экземпляр книги).
     *
     * Каждый кандидат сохраняется как отдельная карточка mt_data (мы всё равно ещё
     * не храним ни одной из них). Если кандидатов больше одного — все они помечаются
     * SourceStatus = "needs_review_multiple_candidates", чтобы вызывающая сторона
     * могла:
     *   - показать пользователю список карточек для выбора нужной;
     *   - либо на своей стороне сопоставить с дополнительными данными (штрихкод,
     *     фото, год на титульном листе) и оставить одну, удалив/пометив остальные.
     *
     * Дедупликация каждого кандидата происходит независимо — по точному совпадению
     * поля Title (см. persist()), поэтому повторный запуск того же поиска не
     * создаёт дублей, а дообогащает уже сохранённые карточки.
     */
    @Transactional
    public List<MtData> enrichByTitleMultiple(Integer orgId, String title) {
        List<MtData> saved = new ArrayList<>();
        List<String> attempted = new ArrayList<>();

        JsonNode olDoc = openLibraryClient.findByTitle(title);
        attempted.add("openlibrary");
        if (olDoc != null) {
            saved.add(persist(orgId, "Title", title, mapOpenLibrarySearch(olDoc), "openlibrary", 0.5));
        }

        JsonNode gbVolume = googleBooksClient.searchByTitle(title, 1);
        attempted.add("googlebooks");
        if (gbVolume != null) {
            saved.add(persist(orgId, "Title", title, mapGoogleBooks(gbVolume, null), "googlebooks", 0.5));
        }

        // Sigla может вернуть много кандидатов для родовых заглавий — это и есть штатный случай,
        // а не ошибка, которую нужно "разрешать" угадыванием.
        List<SiglaClient.SiglaRecord> siglaRecords = siglaClient.search(title);
        attempted.add("sigla");
        if (!siglaRecords.isEmpty()) {
            boolean ambiguous = siglaRecords.size() > 1;
            for (SiglaClient.SiglaRecord r : siglaRecords) {
                Map<String, String> values = mapSigla(r);
                if (ambiguous) {
                    values.put("SourceStatus", "needs_review_multiple_candidates");
                }
                // Дедупим по точному заглавию конкретного издания (оно у каждого кандидата разное,
                // включая подзаголовок/составителей) — НЕ по исходному запросу title.
                saved.add(persist(orgId, "Title", r.title(), values, "sigla", ambiguous ? 0.3 : 0.55));
            }
            log.info("Sigla: для '{}' сохранено {} карточек-кандидатов (ambiguous={})",
                    title, siglaRecords.size(), ambiguous);
        }

        JsonNode flEdition = fantLabClient.findByTitle(title);
        attempted.add("fantlab");
        if (flEdition != null) {
            saved.add(persist(orgId, "Title", title, mapFantLab(flEdition, null), "fantlab", 0.5));
        }

        if (saved.isEmpty()) {
            if (llmFallbackEnabled) {
                JsonNode llmResult = llmFallbackClient.findByTitle(title);
                attempted.add("llm_fallback");
                if (llmResult != null) {
                    LlmSuggestion suggestion = saveSuggestion(orgId, null, title, llmResult);
                    log.info("Книга '{}' восстановлена через LLM fallback -> llm_suggestions#{} (требует ручной модерации)",
                            title, suggestion.getId());
                    return saved; // пусто — предложение ушло на модерацию, не в mt_data напрямую
                }
            }
            saveUnresolved(orgId, null, title, null, attempted);
            log.info("Book '{}' not found by title in any source (attempted: {}), saved to unresolved_lookup", title, attempted);
        }

        return saved;
    }

    private Map<String, String> mapSigla(SiglaClient.SiglaRecord record) {
        Map<String, String> values = new LinkedHashMap<>();
        putIfPresent(values, "ISBN", record.isbn());
        putIfPresent(values, "Title", record.title());
        putIfPresent(values, "AuthorName", record.author());
        putIfPresent(values, "Publisher", record.publisher());
        putIfPresent(values, "PublishedYear", record.year());

        values.put("SourceStatus", "enriched_sigla_nb_msu");
        return values;
    }
}