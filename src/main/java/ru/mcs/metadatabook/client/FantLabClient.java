package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * FantLab API — полностью открытый, без ключей и регистрации.
 * Документация: https://github.com/FantLab/FantLab-API/blob/master/Docs/search-bibliography.md
 *
 * GET https://api.fantlab.ru/search-editions?q={query}&onlymatches=1
 * Если q — это ISBN, сервер сам ищет по нему (подтверждено официальной документацией).
 * Покрытие — преимущественно фантастика/фэнтези на русском языке.
 *
 * Поля ответа (autors, name, publisher, series) могут содержать bb-теги вида
 * [b]...[/b] — их нужно чистить перед записью в mt_data.
 */
@Component
@Slf4j
public class FantLabClient {

    private static final String BASE_URL = "https://api.fantlab.ru/search-editions";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    /** Возвращает первое найденное издание (edition) по ISBN, либо null. */
    public JsonNode findByIsbn(String isbn) {
        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        String url = BASE_URL + "?q=" + normalizedIsbn + "&onlymatches=1";

        try {
            String raw = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray() || root.isEmpty()) {
                log.debug("FantLab: ISBN {} not found", isbn);
                return null;
            }
            return root.get(0);
        } catch (Exception e) {
            log.error("FantLab request failed for ISBN {}", isbn, e);
            return null;
        }
    }

    /** Убирает bb-теги вида [b]...[/b], [url=...]...[/url] из строковых полей FantLab. */
    public static String stripBbCode(String value) {
        if (value == null) return null;
        return value.replaceAll("\\[/?\\w+(=[^\\]]*)?\\]", "").trim();
    }
}