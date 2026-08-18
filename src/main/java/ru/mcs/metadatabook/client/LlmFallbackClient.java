package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Крайний фолбэк-источник метаданных: SearXNG (веб-поиск) + локальная LLM
 * через Ollama. Используется только когда структурированные источники
 * (Open Library, Google Books, FantLab, LibGen) ничего не нашли.
 *
 * Поддерживает два входа: findByIsbn (для книг с ISBN) и findByTitle (для
 * изданий без ISBN — старые советские книги, малотиражные издания и т.п.).
 * Оба используют один и тот же детерминированный пайплайн: сами формируем
 * запрос -> SearXNG -> модель извлекает поля из сниппетов -> верификация
 * каждого поля против текста найденного источника.
 *
 * Отключено по умолчанию (llm.fallback.enabled=false).
 */
@Component
@Slf4j
public class LlmFallbackClient {

    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "не указано", "none", "n/a", "неизвестно", "нет данных", "",
            "not specified", "unknown", "null", "не найдено", "no data"
    );

    private static final List<String> VERIFIABLE_FIELDS = List.of("author", "year", "publisher", "isbn");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.fallback.searxng-url}")
    private String searxngUrl;

    @Value("${llm.fallback.ollama-url}")
    private String ollamaUrl;

    @Value("${llm.fallback.model}")
    private String model;

    @Value("${llm.fallback.timeout-seconds:180}")
    private int timeoutSeconds;

    private final RestClient searxngClient = RestClient.builder().build();
    private final RestClient ollamaClient = RestClient.builder()
            .requestFactory(requestFactoryWithTimeout())
            .build();

    /** Поиск по ISBN. Возвращает ObjectNode с полями либо null. */
    public JsonNode findByIsbn(String isbn) {
        String query = "ISBN " + isbn + " книга название автор издательство год";
        String identifier = "ISBN " + isbn;
        return runFallback(query, identifier);
    }

    /**
     * Поиск по названию — для изданий без ISBN (старые книги и т.п.).
     * Точность ниже, чем поиск по ISBN, так как совпадение названия
     * менее однозначно идентифицирует конкретное издание.
     */
    public JsonNode findByTitle(String title) {
        String query = "\"" + title + "\" книга автор издательство год издания";
        String identifier = "названием \"" + title + "\"";
        return runFallback(query, identifier);
    }

    private JsonNode runFallback(String searchQuery, String identifierDescription) {
        List<SearchResult> results = webSearch(searchQuery);

        if (results.isEmpty()) {
            log.debug("LLM fallback: SearXNG вернул пустую выдачу для {}", identifierDescription);
            return null;
        }

        ObjectNode raw = callOllama(identifierDescription, formatResultsForModel(results));
        if (raw == null) {
            log.warn("LLM fallback: модель не вернула валидный JSON для {}", identifierDescription);
            return null;
        }

        ObjectNode verified = verify(raw, results);
        boolean anythingConfirmed = VERIFIABLE_FIELDS.stream()
                .anyMatch(f -> verified.hasNonNull(f));

        if (!anythingConfirmed && !verified.hasNonNull("source_url")) {
            log.info("LLM fallback: ни одно поле не подтверждено источником для {}", identifierDescription);
            return null;
        }

        return verified;
    }

    private List<SearchResult> webSearch(String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String raw = searxngClient.get()
                    .uri(searxngUrl + "/search?q={q}&format=json&language=ru", query)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.get("results");
            if (items != null && items.isArray()) {
                int limit = 0;
                for (JsonNode item : items) {
                    if (limit++ >= 5) break;
                    String title = textOrEmpty(item, "title");
                    String content = textOrEmpty(item, "content");
                    String url = textOrEmpty(item, "url");
                    results.add(new SearchResult(title, content, url));
                }
            }
        } catch (Exception e) {
            log.error("LLM fallback: ошибка запроса к SearXNG", e);
        }
        return results;
    }

    private String formatResultsForModel(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (SearchResult r : results) {
            sb.append("[").append(i++).append("] ").append(r.title()).append("\n")
                    .append(r.content()).append("\n")
                    .append("URL: ").append(r.url()).append("\n\n");
        }
        return sb.toString();
    }

    private ObjectNode callOllama(String identifierDescription, String searchContext) {
        String system = """
                Используй ТОЛЬКО данные из предоставленных результатов поиска.
                Не используй собственные знания о книгах.
                Ответ строго в формате JSON со следующими полями:
                title (строка или null), author (строка или null), year (число или null),
                publisher (строка или null), isbn (строка или null), source_url (строка или null).
                ЗАПРЕЩЕНО использовать строки 'не указано', 'None', 'неизвестно',
                'not specified', 'unknown' или подобные — если данных нет, используй JSON null.
                source_url ОБЯЗАН быть скопирован дословно из результатов поиска.
                Заполняй author/year/publisher ТОЛЬКО если значение буквально
                присутствует в тексте выбранного результата — иначе null.
                Не добавляй никакого текста вне JSON-объекта.
                """;

        String userMessage = "Найди метаданные книги по " + identifierDescription
                + ".\n\nРезультаты поиска:\n" + searchContext;

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("stream", false);
        payload.put("format", "json");

        var messages = objectMapper.createArrayNode();
        messages.add(objectMapper.createObjectNode().put("role", "system").put("content", system));
        messages.add(objectMapper.createObjectNode().put("role", "user").put("content", userMessage));
        payload.set("messages", messages);

        try {
            String raw = ollamaClient.post()
                    .uri(ollamaUrl + "/api/chat")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(raw);
            if (response.has("error")) {
                log.error("LLM fallback: Ollama вернула ошибку: {}", response.get("error").asText());
                return null;
            }

            String content = response.path("message").path("content").asText(null);
            if (content == null || content.isBlank()) return null;

            JsonNode parsed = objectMapper.readTree(content);
            return parsed.isObject() ? (ObjectNode) parsed : null;
        } catch (Exception e) {
            log.error("LLM fallback: ошибка запроса к Ollama", e);
            return null;
        }
    }

    private ObjectNode verify(ObjectNode record, List<SearchResult> results) {
        normalizePlaceholders(record);

        String sourceUrl = record.path("source_url").asText(null);
        SearchResult match = results.stream()
                .filter(r -> r.url().equals(sourceUrl))
                .findFirst()
                .orElse(null);

        if (match == null) {
            log.warn("LLM fallback: source_url '{}' не найден дословно в результатах поиска — обнуляю поля", sourceUrl);
            Iterator<String> names = record.fieldNames();
            List<String> toNull = new ArrayList<>();
            names.forEachRemaining(toNull::add);
            for (String field : toNull) {
                if (!field.equals("title")) record.putNull(field);
            }
            return record;
        }

        String snippetText = normalizeText(match.title() + " " + match.content());
        for (String field : VERIFIABLE_FIELDS) {
            String value = record.path(field).isNull() ? null : record.path(field).asText(null);
            if (value == null) continue;
            if (!snippetText.contains(normalizeText(value))) {
                log.warn("LLM fallback: поле '{}'='{}' не подтверждено текстом источника — обнулено", field, value);
                record.putNull(field);
            }
        }
        return record;
    }

    private void normalizePlaceholders(ObjectNode record) {
        Iterator<String> names = record.fieldNames();
        List<String> fields = new ArrayList<>();
        names.forEachRemaining(fields::add);
        for (String field : fields) {
            JsonNode value = record.get(field);
            if (value != null && value.isTextual()
                    && PLACEHOLDER_VALUES.contains(value.asText().trim().toLowerCase(Locale.ROOT))) {
                record.putNull(field);
            }
        }
    }

    private String normalizeText(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? "" : value.asText();
    }

    private org.springframework.http.client.ClientHttpRequestFactory requestFactoryWithTimeout() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10).toMillisPart() + 10_000);
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return factory;
    }

    private record SearchResult(String title, String content, String url) {}
}