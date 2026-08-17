package ru.mcs.metadatabook.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.client.FantLabClient;
import ru.mcs.metadatabook.client.GoogleBooksClient;
import ru.mcs.metadatabook.client.LibGenClient;
import ru.mcs.metadatabook.client.OpenLibraryClient;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.UnresolvedLookup;
import ru.mcs.metadatabook.repository.UnresolvedLookupRepository;

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
 * Порядок: Open Library -> Google Books -> FantLab -> LibGen (если включён) ->
 * (если ничего) unresolved_lookup.
 *
 * LibGen — последний по приоритету: он выключен по умолчанию и даёт менее
 * надёжные совпадения (матчит скорее по произведению, чем по конкретному изданию).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicSourcesEnrichmentService {

    private final OpenLibraryClient openLibraryClient;
    private final GoogleBooksClient googleBooksClient;
    private final FantLabClient fantLabClient;
    private final LibGenClient libGenClient;
    private final BookQueryService bookQueryService;
    private final BookWriteService bookWriteService;
    private final UnresolvedLookupRepository unresolvedLookupRepository;

    @Transactional
    public MtData enrichByIsbn(Integer orgId, String isbn) {
        List<String> attempted = new ArrayList<>();

        JsonNode olBook = openLibraryClient.findByIsbn(isbn);
        attempted.add("openlibrary");
        if (olBook != null) {
            return persist(orgId, isbn, mapOpenLibrary(olBook, isbn), "openlibrary", 0.7);
        }

        JsonNode gbVolume = googleBooksClient.findByIsbn(isbn);
        attempted.add("googlebooks");
        if (gbVolume != null) {
            return persist(orgId, isbn, mapGoogleBooks(gbVolume, isbn), "googlebooks", 0.7);
        }

        JsonNode flEdition = fantLabClient.findByIsbn(isbn);
        attempted.add("fantlab");
        if (flEdition != null) {
            return persist(orgId, isbn, mapFantLab(flEdition, isbn), "fantlab", 0.65);
        }

        JsonNode lgEdition = libGenClient.findByIsbn(isbn);
        attempted.add("libgen");
        if (lgEdition != null) {
            return persist(orgId, isbn, mapLibGen(lgEdition, isbn), "libgen", 0.4);
        }

        saveUnresolved(orgId, isbn, null, null, attempted);
        log.info("ISBN {} not found in any public source, saved to unresolved_lookup", isbn);
        return null;
    }

    private MtData persist(Integer orgId, String isbn, Map<String, String> values, String source, double confidence) {
        var bookObject = bookQueryService.getBookObject(orgId);
        var existing = bookQueryService.findByIndexedField(orgId, bookObject.getObjId(), "ISBN", isbn);

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

        values.put("SourceStatus", "enriched_googlebooks");
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

        values.put("SourceStatus", "enriched_fantlab");
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

    private void saveUnresolved(Integer orgId, String isbn, String title, String authorName, List<String> attempted) {
        UnresolvedLookup existing = unresolvedLookupRepository.findByOrgIdAndIsbn(orgId, isbn).orElse(null);

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
}