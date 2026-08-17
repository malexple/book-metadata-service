package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.mcs.metadatabook.config.LibGenProperties;

import java.util.Iterator;
import java.util.Map;

/**
 * LibGen (libgen.li) json.php API — открытый, без ключей, но ЮРИДИЧЕСКИ СЕРАЯ ЗОНА
 * (теневая библиотека). Выключен по умолчанию (libgen.enabled=false), включается явно.
 *
 * GET https://libgen.li/json.php?object=e&addkeys=isbn&isbn={isbn}&fields=title,author,publisher,year,pages,cover_url
 *
 * ВАЖНО: ответ — JSON-ОБЪЕКТ (не массив!), ключи — edition_id, значения — данные издания.
 * Может быть замечено по факту с ISBN "Голодных игр": сервер матчит скорее по "work"
 * (произведению), чем строго по ISBN конкретного издания — поэтому на один ISBN может
 * прийти десяток разнородных записей (разные годы/издательства/языки одной книги).
 * Из-за этого доверие (confidence) к результату LibGen ниже, чем к остальным источникам.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LibGenClient {

    private final LibGenProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    /** Возвращает "лучшую" запись издания по эвристике полноты данных, либо null. */
    public JsonNode findByIsbn(String isbn) {
        if (!properties.isEnabled()) {
            log.debug("LibGen disabled via libgen.enabled=false");
            return null;
        }

        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        String url = properties.getBaseUrl()
                + "?object=e&addkeys=isbn&isbn=" + normalizedIsbn
                + "&fields=title,author,publisher,year,pages,cover_url";

        try {
            String raw = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(raw);

            if (!root.isObject() || root.isEmpty()) {
                log.debug("LibGen: ISBN {} not found", isbn);
                return null;
            }

            return pickBestMatch(root);
        } catch (Exception e) {
            log.error("LibGen request failed for ISBN {}", isbn, e);
            return null;
        }
    }

    /**
     * Эвристика выбора лучшей записи среди множества изданий/сканов одной книги:
     * приоритет записям с непустым автором, издательством и наличием файлов
     * (значит, это не просто пустая карточка метаданных, а реально существующий скан).
     */
    private JsonNode pickBestMatch(JsonNode root) {
        JsonNode best = null;
        int bestScore = -1;

        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            JsonNode entry = fields.next().getValue();
            String title = entry.path("title").asText("");
            if (title.isBlank()) continue;

            int score = 0;
            if (!entry.path("author").asText("").isBlank()) score++;
            if (!entry.path("publisher").asText("").isBlank()) score++;
            if (entry.has("files") && entry.get("files").size() > 0) score++;

            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }
        return best;
    }
}