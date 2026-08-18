package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Open Library Books API — полностью открытый, без регистрации и ключей.
 * https://openlibrary.org/dev/docs/api/books
 *
 * findByIsbn: GET https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data
 * Возвращает объект вида {"ISBN:9785171234567": {title, authors, publishers, ...}}
 * или {} если ничего не найдено (HTTP 200 в обоих случаях — важно проверять пустоту).
 *
 * findByTitle: GET https://openlibrary.org/search.json?title={title}&limit=1
 * Формат ответа ("docs") ОТЛИЧАЕТСЯ от findByIsbn: author_name/publisher — плоские
 * массивы строк (не объектов), first_publish_year вместо publish_date, cover_i
 * (числовой id) вместо объекта cover. Поэтому в сервисе используется отдельный
 * маппер (mapOpenLibrarySearch), а не общий mapOpenLibrary.
 */
@Component
@Slf4j
public class OpenLibraryClient {

    private static final String BOOKS_URL = "https://openlibrary.org/api/books";
    private static final String SEARCH_URL = "https://openlibrary.org/search.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    /** Возвращает JSON-объект книги или null, если не найдено/ошибка. */
    public JsonNode findByIsbn(String isbn) {
        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        String url = BOOKS_URL + "?bibkeys=ISBN:" + normalizedIsbn + "&format=json&jscmd=data";

        try {
            String raw = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode book = root.get("ISBN:" + normalizedIsbn);
            if (book == null || book.isEmpty()) {
                log.debug("Open Library: ISBN {} not found", isbn);
                return null;
            }
            return book;
        } catch (Exception e) {
            log.error("Open Library request failed for ISBN {}", isbn, e);
            return null;
        }
    }

    /**
     * Поиск по названию — для книг без ISBN (старые издания, СССР и т.п.).
     * Возвращает первый (наиболее релевантный по умолчанию сортировке Open Library)
     * документ из "docs", либо null, если ничего не найдено.
     */
    public JsonNode findByTitle(String title) {
        String url = SEARCH_URL + "?title=" + java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8)
                + "&limit=1";

        try {
            String raw = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode docs = root.get("docs");
            if (docs == null || !docs.isArray() || docs.isEmpty()) {
                log.debug("Open Library: title '{}' not found", title);
                return null;
            }
            return docs.get(0);
        } catch (Exception e) {
            log.error("Open Library title search failed for '{}'", title, e);
            return null;
        }
    }
}