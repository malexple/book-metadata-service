package ru.mcs.metadatabook.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.client.LitresClient;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.UnresolvedLookup;
import ru.mcs.metadatabook.repository.UnresolvedLookupRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Обогащение книги данными из Litres. Порядок действий для ISBN:
 * 1) точный поиск (strict=exact) по ISBN как строке запроса;
 * 2) если не найдено — считаем книгу неразрешённой и кладём в unresolved_lookup
 *    (fallback на Ozon подключается сюда же следующим шагом, как отдельный источник).
 *
 * Найденное значение пишется через BookWriteService с source="litres" и confidence
 * на основе match_weight, возвращённого Litres (0..100 -> 0..1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LitresEnrichmentService {

    private static final int SEARCH_LIMIT = 5;

    private final LitresClient litresClient;
    private final BookQueryService bookQueryService;
    private final BookWriteService bookWriteService;
    private final UnresolvedLookupRepository unresolvedLookupRepository;

    @Transactional
    public MtData enrichByIsbn(Integer orgId, String isbn) {
        List<JsonNode> arts = litresClient.searchArts(isbn, "exact", SEARCH_LIMIT);

        JsonNode match = arts.stream()
                .filter(a -> isbn.equalsIgnoreCase(textOrNull(a, "isbn")))
                .findFirst()
                .orElse(arts.isEmpty() ? null : arts.get(0));

        if (match == null) {
            saveUnresolved(orgId, isbn, null, null, "litres");
            log.info("Litres: ISBN {} not found, saved to unresolved_lookup", isbn);
            return null;
        }

        Map<String, String> values = mapArtToBookFields(match);
        values.putIfAbsent("ISBN", isbn);
        double confidence = extractConfidence(match);

        var existing = bookQueryService.findByIndexedField(orgId, bookQueryService.getBookObject(orgId).getObjId(), "ISBN", isbn);
        if (!existing.isEmpty()) {
            return bookWriteService.enrichBook(orgId, existing.get(0).getGuid(), values, "litres", confidence);
        }
        return bookWriteService.createBook(orgId, values, "litres", confidence);
    }

    @Transactional
    public MtData enrichByTitle(Integer orgId, String title, String authorName) {
        List<JsonNode> arts = litresClient.searchArts(title, "no", SEARCH_LIMIT);

        JsonNode best = arts.stream()
                .filter(a -> authorName == null || authorName.isBlank() || personsContain(a, authorName))
                .max((a, b) -> Integer.compare(matchWeight(a), matchWeight(b)))
                .orElse(null);

        if (best == null) {
            saveUnresolved(orgId, null, title, authorName, "litres");
            log.info("Litres: title '{}' not found, saved to unresolved_lookup", title);
            return null;
        }

        Map<String, String> values = mapArtToBookFields(best);
        double confidence = extractConfidence(best);
        return bookWriteService.createBook(orgId, values, "litres", confidence);
    }

    private void saveUnresolved(Integer orgId, String isbn, String title, String authorName, String attemptedSource) {
        UnresolvedLookup existing = isbn != null
                ? unresolvedLookupRepository.findByOrgIdAndIsbn(orgId, isbn).orElse(null)
                : null;

        if (existing != null) {
            List<String> sources = new java.util.ArrayList<>(java.util.Arrays.asList(existing.getAttemptedSources()));
            if (!sources.contains(attemptedSource)) sources.add(attemptedSource);
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
                .attemptedSources(new String[]{attemptedSource})
                .status("pending")
                .rawContext(new HashMap<>())
                .build();
        unresolvedLookupRepository.save(lookup);
    }

    private Map<String, String> mapArtToBookFields(JsonNode art) {
        Map<String, String> values = new LinkedHashMap<>();

        putIfPresent(values, "ExternalLitresId", textOrNull(art, "id"));
        putIfPresent(values, "Title", textOrNull(art, "title"));
        putIfPresent(values, "Subtitle", textOrNull(art, "subtitle"));
        putIfPresent(values, "ISBN", textOrNull(art, "isbn"));
        putIfPresent(values, "Language", textOrNull(art, "lang"));
        putIfPresent(values, "Publisher", textOrNull(art, "publisher"));
        putIfPresent(values, "AgeRestriction", textOrNull(art, "minage"));
        putIfPresent(values, "Description", stripHtml(textOrNull(art, "annotation")));

        String authors = joinField(art.get("persons"), "full_name");
        putIfPresent(values, "AuthorName", authors);

        String genres = joinField(art.get("genres"), "name");
        putIfPresent(values, "Genre", genres);

        String litresId = textOrNull(art, "id");
        if (litresId != null) {
            values.put("CoverUrl", LitresClient.buildCoverUrl(litresId, 200));
        }

        values.put("SourceStatus", "enriched_litres");

        return values;
    }

    private void putIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private String joinField(JsonNode array, String field) {
        if (array == null || !array.isArray()) return null;
        return StreamSupport.stream(array.spliterator(), false)
                .map(n -> textOrNull(n, field))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private boolean personsContain(JsonNode art, String authorName) {
        JsonNode persons = art.get("persons");
        if (persons == null || !persons.isArray()) return false;
        String needle = authorName.toLowerCase();
        return StreamSupport.stream(persons.spliterator(), false)
                .map(p -> textOrNull(p, "full_name"))
                .filter(java.util.Objects::nonNull)
                .anyMatch(name -> name.toLowerCase().contains(needle));
    }

    private int matchWeight(JsonNode art) {
        JsonNode w = art.get("match_weight");
        return w != null ? w.asInt(0) : 0;
    }

    private double extractConfidence(JsonNode art) {
        return matchWeight(art) / 100.0;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private String stripHtml(String html) {
        return html == null ? null : html.replaceAll("<[^>]*>", "").trim();
    }
}