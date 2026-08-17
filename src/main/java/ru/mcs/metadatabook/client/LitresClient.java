package ru.mcs.metadatabook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import ru.mcs.metadatabook.config.LitresProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Тонкий клиент протокола catalitv2 (см. https://docs.litres.ru/public/6424300.html
 * и https://docs.litres.ru/public/6424308.html).
 *
 * ВАЖНЫЕ НЮАНСЫ ПРОТОКОЛА (из-за которых поиск молча возвращал пустой результат):
 * 1) Поле "sid" для анонимных запросов должно ОТСУТСТВОВАТЬ в JSON целиком,
 *    а не быть пустой строкой "" — иначе сервер трактует это как невалидный SID
 *    (error_code 101000) и просто не выполняет вложенный запрос.
 * 2) Аналогично "app"/"sha" не нужно передавать вообще, если не настроены —
 *    "Для анонимных запросов не требуется".
 * 3) Сервер может ответить с Content-Encoding: gzip. java.net.http.HttpClient
 *    (на нём построен RestClient.create()) НЕ распаковывает gzip сам —
 *    просим сервер явно не сжимать ответ через Accept-Encoding: identity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LitresClient {

    private final LitresProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    private volatile long lastRequestAtMillis = 0L;

    public List<JsonNode> searchArts(String query, String strict, int limit) {
        if (!properties.isEnabled()) {
            log.warn("Litres integration disabled via litres.enabled=false");
            return List.of();
        }

        throttle();

        String requestId = "search_" + UUID.randomUUID().toString().substring(0, 8);
        String jdata = buildRequestJson(requestId, query, strict, limit);
        log.debug("Litres request jdata: {}", jdata);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("jdata", jdata);

        try {
            String rawResponse = restClient.post()
                    .uri(properties.getBaseUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                    .body(form)
                    .retrieve()
                    .body(String.class);

            log.debug("Litres raw response: {}", rawResponse);
            return parseArts(rawResponse, requestId);
        } catch (Exception e) {
            log.error("Litres request failed for query='{}'", query, e);
            return List.of();
        }
    }

    private void throttle() {
        long now = System.currentTimeMillis();
        long wait = properties.getMinRequestIntervalMs() - (now - lastRequestAtMillis);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAtMillis = System.currentTimeMillis();
    }

    private String buildRequestJson(String requestId, String query, String strict, int limit) {
        String time = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        var root = objectMapper.createObjectNode();
        root.put("time", time);
        // ВАЖНО: sid/app/sha намеренно НЕ добавляются для анонимного запроса —
        // добавление пустых строк ломает запрос (см. javadoc класса).
        if (properties.getAppId() != null && !properties.getAppId().isBlank()
                && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            root.put("app", properties.getAppId());
            root.put("sha", sha256Hex(time + properties.getSecretKey()));
        }

        var param = objectMapper.createObjectNode();
        param.put("q", query);
        param.put("strict", strict);
        param.putArray("limit").add("0").add(String.valueOf(limit));
        param.put("anno", "1");
        param.put("currency", "RUB");

        var request = objectMapper.createObjectNode();
        request.put("func", "r_search_arts");
        request.put("id", requestId);
        request.set("param", param);

        root.putArray("requests").add(request);

        return root.toString();
    }

    private List<JsonNode> parseArts(String rawResponse, String requestId) {
        try {
            JsonNode response = objectMapper.readTree(rawResponse);

            if (!response.path("success").asBoolean(false)) {
                log.warn("Litres top-level request failed: {}", rawResponse);
                return List.of();
            }

            JsonNode searchResult = response.get(requestId);
            if (searchResult == null) {
                log.warn("Litres response has no key '{}': {}", requestId, rawResponse);
                return List.of();
            }
            if (!searchResult.path("success").asBoolean(false)) {
                log.warn("Litres search_arts sub-request failed (error_code={}): {}",
                        searchResult.path("error_code").asText("?"), rawResponse);
                return List.of();
            }

            JsonNode arts = searchResult.get("arts");
            List<JsonNode> result = new ArrayList<>();
            if (arts != null && arts.isArray()) {
                arts.forEach(result::add);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse Litres response (possibly gzip/encoding issue): {}", rawResponse, e);
            return List.of();
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Строит URL обложки по алгоритму из документации (предпоследняя цифра ID книги = номер сервера). */
    public static String buildCoverUrl(String litresId, int width) {
        if (litresId == null || litresId.isBlank()) return null;
        String digits = litresId.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        int serverDigit = digits.length() >= 2
                ? Character.getNumericValue(digits.charAt(digits.length() - 2))
                : 0;
        return "https://cv" + serverDigit + ".litres.ru/pub/c/cover_" + width + "/" + digits + ".jpg";
    }
}