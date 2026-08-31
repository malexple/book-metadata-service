package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.mcs.metadatabook.config.GoogleBooksProperties;

/**
 * Google Books API. С 2024-2026 анонимная (без ключа) квота на общий пул
 * запросов со всего мира периодически обнуляется Google (quota_limit_value=0,
 * 429 RESOURCE_EXHAUSTED) — на практике API без ключа работать перестал.
 * Поэтому ключ теперь обязателен для стабильной работы, хотя формально бесплатен.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleBooksClient {

    private static final String BASE_URL = "https://www.googleapis.com/books/v1/volumes";

    private final GoogleBooksProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public JsonNode findByIsbn(String isbn) {
        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        String url = withKey(BASE_URL + "?q=isbn:" + normalizedIsbn);

        try {
            String raw = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                log.debug("Google Books: ISBN {} not found", isbn);
                return null;
            }
            return items.get(0).get("volumeInfo");
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.error("Google Books quota exceeded. Configure google.books.api-key " +
                    "(console.cloud.google.com -> Books API -> Credentials).");
            return null;
        } catch (Exception e) {
            log.error("Google Books request failed for ISBN {}", isbn, e);
            return null;
        }
    }

    private String withKey(String url) {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            return url + "&key=" + properties.getApiKey();
        }
        log.warn("google.books.api-key not configured — anonymous quota is likely exhausted (429)");
        return url;
    }

    public java.util.List<JsonNode> searchByTitle(String title, int limit) {
        try {
            String raw = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("www.googleapis.com")
                                .path("/books/v1/volumes")
                                .queryParam("q", "intitle:" + title)
                                .queryParam("maxResults", limit);
                        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                            builder.queryParam("key", properties.getApiKey());
                        } else {
                            log.warn("google.books.api-key not configured — anonymous quota is likely exhausted (429)");
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray() || items.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<JsonNode> result = new java.util.ArrayList<>();
            items.forEach(item -> {
                JsonNode volumeInfo = item.get("volumeInfo");
                if (volumeInfo != null) result.add(volumeInfo);
            });
            return result;
        } catch (Exception e) {
            log.error("Google Books title search failed for '{}'", title, e);
            return java.util.List.of();
        }
    }
}