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
 * GET https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data
 * Возвращает объект вида {"ISBN:9785171234567": {title, authors, publishers, ...}}
 * или {} если ничего не найдено (HTTP 200 в обоих случаях — важно проверять пустоту).
 */
@Component
@Slf4j
public class OpenLibraryClient {

    private static final String BASE_URL = "https://openlibrary.org/api/books";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    /** Возвращает JSON-объект книги или null, если не найдено/ошибка. */
    public JsonNode findByIsbn(String isbn) {
        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        String url = BASE_URL + "?bibkeys=ISBN:" + normalizedIsbn + "&format=json&jscmd=data";

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
}