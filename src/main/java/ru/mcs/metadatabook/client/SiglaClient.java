package ru.mcs.metadatabook.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.mcs.metadatabook.config.SiglaProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Клиент для sigla.ru (Библиотечная Компьютерная Сеть, НБ МГУ им. Ломоносова) —
 * публичный веб-метапоиск по нескольким российским библиотечным каталогам.
 *
 * НЕ официальный API — HTML-скрапинг legacy JSP-приложения, поэтому:
 *   - выключен по умолчанию (sigla.enabled=false, opt-in, как LibGen) — сайт
 *     создавался для читателей-людей, а не как выделенный API, включайте
 *     осознанно, понимая это;
 *   - собственный троттлинг (sigla.min-request-interval-ms, по умолчанию 2000мс) —
 *     нагрузку на чужую инфраструктуру держим минимальной вне зависимости от
 *     того, сколько людей одновременно обращаются к НАШЕМУ сервису. Это
 *     синхронизировано на уровне JVM (synchronized) — при нескольких
 *     одновременных запросах они выстраиваются в очередь, а не бьют залпом.
 *
 * Известные подводные камни (все обнаружены эмпирически):
 * 1. URL требует ВСЕХ параметров формы, включая пустые (иначе 500 crash.jsp).
 * 2. URL передаётся как java.net.URI, а не String (иначе двойное percent-кодирование).
 * 3. Сервер не шлёт charset в Content-Type — тело читаем как byte[] и
 *    декодируем сами как UTF-8, иначе Spring откатится на ISO-8859-1.
 * 4. Ячейка с порядковым номером строки тоже имеет class="table-text" —
 *    сдвигает индексы: cells.get(0)=номер, (1)=автор, (2)=заглавие, (3)=данные.
 * 5. Таймауты: 5с/10с на подключение/чтение — сайт нестабилен, видели и
 *    зависания, и Connection refused при недоступности. Ошибки логируются
 *    как WARN (не ERROR) — это ОЖИДАЕМЫЙ режим отказа стороннего legacy-сервиса,
 *    а не баг в нашем коде.
 * 6. Пагинация: table.jsp отдаёт только первые 20 записей (psz=20/bs=20).
 * 7. Родовые технические заглавия неразличимы по чистому тексту — см. pickBestMatch
 *    (Jaccard + бонусы за автора/год, при ничьей — null, не гадаем).
 *
 * f=7 — код поля "ISBN" в форме sigla.ru (используется в findByIsbn).
 * f=1016 — "любой элемент" (используется в search(title)/findByTitle).
 * cc=c — каталог "НБ МГУ, Электронный каталог Книг с 1990 г." — единственный
 * эмпирически подтверждённый код на данный момент.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SiglaClient {

    private static final String BASE_URL = "http://www.sigla.ru/table.jsp";
    private static final String STATIC_PARAMS =
            "&o=a&f=1003&t=1&v1=&o=a&f=4&t=2&v2=&o=a&f=21&t=3&v3=&o=a&f=1016&t=3&v4=&o=a&f=1016&t=3&v5="
                    + "&bf=4&b=&d=0&ys=&ye=&lng=&ft=&mt=&dt=&vol=&pt=&iss=&ps=&pe=&tr=&tro="
                    + "&cc=c&i=1&cv=tagged&tv=card&s=0&ss=0&st=0&i18n=ru&rlf=&psz=20&bs=20"
                    + "&ce=g316.......................................................0UT"
                    + "&debug=false";

    private static final double TITLE_MATCH_THRESHOLD = 0.55;
    private static final double TIE_EPSILON = 1e-6;

    private final SiglaProperties properties;

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; book-metadata-service/1.0)")
            .requestFactory(timeoutRequestFactory())
            .build();

    private long lastRequestAtMillis = 0L;

    private static ClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return factory;
    }

    // ---------- Поиск по ISBN ----------

    public SiglaRecord findByIsbn(String isbn) {
        String normalizedIsbn = isbn.replaceAll("[^0-9Xx]", "");
        List<SiglaRecord> results = searchByField(normalizedIsbn, "7");

        if (results.isEmpty()) return null;
        if (results.size() == 1) return results.get(0);

        List<SiglaRecord> exactMatches = results.stream()
                .filter(r -> normalizedIsbn.equalsIgnoreCase(r.isbn()))
                .toList();

        if (exactMatches.size() == 1) return exactMatches.get(0);

        log.warn("Sigla: неоднозначность по ISBN {} — {} записей, ни одна не выделяется однозначно — не возвращаю",
                normalizedIsbn, results.size());
        return null;
    }

    // ---------- Поиск по названию ----------

    public SiglaRecord findByTitle(String title) {
        return findByTitle(title, null, null);
    }

    public SiglaRecord findByTitle(String title, String authorHint, String yearHint) {
        List<SiglaRecord> records = search(title);
        return pickBestMatch(title, authorHint, yearHint, records);
    }

    public List<SiglaRecord> search(String title) {
        return searchByField(title, "1016");
    }

    // ---------- Общая механика запроса/парсинга ----------

    private List<SiglaRecord> searchByField(String query, String fieldCode) {
        List<SiglaRecord> results = new ArrayList<>();

        if (!properties.isEnabled()) {
            log.debug("Sigla disabled via sigla.enabled=false, skipping query '{}'", query);
            return results;
        }

        throttle();

        String rawUrl = BASE_URL + "?o=a&f=" + fieldCode + "&t=3&v0="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + STATIC_PARAMS;

        try {
            URI uri = URI.create(rawUrl);

            byte[] rawBody = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (rawBody == null) return results;
            String html = new String(rawBody, StandardCharsets.UTF_8);

            Document doc = Jsoup.parse(html);

            if (doc.title() != null && doc.title().contains("Серьезная ошибка")) {
                log.warn("Sigla: сервер вернул crash.jsp для запроса '{}' (field={})", query, fieldCode);
                return results;
            }

            Elements rows = doc.select("tr.table-body");
            log.debug("Sigla: получено {} строк результатов для '{}' (field={})", rows.size(), query, fieldCode);

            for (Element row : rows) {
                Elements cells = row.select("td.table-text");
                if (cells.size() < 4) continue;

                String author = cells.get(1).text().trim();
                String rawTitle = cells.get(2).text();
                String cleanTitle = stripElectronicResourceSuffix(rawTitle);
                String cityPublisherYear = cells.get(3).text().trim();

                String isbn = null;
                Element actionLink = row.selectFirst("a[href*=openurl.jsp]");
                if (actionLink != null) {
                    isbn = extractQueryParam(actionLink.attr("href"), "isbn");
                }

                if (cleanTitle.isBlank()) continue;

                results.add(new SiglaRecord(
                        author.isBlank() ? null : author,
                        cleanTitle,
                        extractCity(cityPublisherYear),
                        extractPublisher(cityPublisherYear),
                        extractYear(cityPublisherYear),
                        (isbn == null || isbn.isBlank()) ? null : isbn.replaceAll("[^0-9Xx]", "")
                ));
            }
        } catch (Exception e) {
            // WARN, не ERROR: недоступность нестабильного стороннего legacy-сервиса —
            // ожидаемый режим отказа, не баг в нашем коде (см. класс-комментарий).
            log.warn("Sigla: ошибка запроса для '{}' (field={}): {}", query, fieldCode, e.getMessage());
        }

        return results;
    }

    /**
     * Собственный троттлинг — не чаще одного запроса к sigla.ru за
     * sigla.min-request-interval-ms миллисекунд, независимо от того, сколько
     * параллельных запросов сейчас обрабатывает наш сервис. synchronized
     * гарантирует, что при конкурентных вызовах они выстроятся в очередь,
     * а не отправят несколько запросов одновременно.
     */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestAtMillis;
        long waitMs = properties.getMinRequestIntervalMs() - elapsed;

        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAtMillis = System.currentTimeMillis();
    }

    private SiglaRecord pickBestMatch(String query, String authorHint, String yearHint, List<SiglaRecord> records) {
        if (records.isEmpty()) return null;

        Set<String> queryWords = normalizeWords(query);
        SiglaRecord best = null;
        double bestScore = -1;
        boolean tie = false;

        for (SiglaRecord r : records) {
            Set<String> titleWords = normalizeWords(r.title());
            Set<String> intersection = new LinkedHashSet<>(queryWords);
            intersection.retainAll(titleWords);
            Set<String> union = new LinkedHashSet<>(queryWords);
            union.addAll(titleWords);

            double titleScore = union.isEmpty() ? 0 : (double) intersection.size() / union.size();

            double authorBonus = 0;
            if (authorHint != null && !authorHint.isBlank() && r.author() != null) {
                Set<String> authorHintWords = normalizeWords(authorHint);
                Set<String> recordAuthorWords = normalizeWords(r.author());
                Set<String> authorIntersection = new LinkedHashSet<>(authorHintWords);
                authorIntersection.retainAll(recordAuthorWords);
                if (!authorIntersection.isEmpty()) authorBonus = 0.5;
            }

            double yearBonus = 0;
            if (yearHint != null && !yearHint.isBlank() && yearHint.equals(r.year())) {
                yearBonus = 0.3;
            }

            double score = titleScore + authorBonus + yearBonus;
            log.debug("Sigla: titleScore={} authorBonus={} yearBonus={} total={} для '{}' (автор='{}', год={})",
                    titleScore, authorBonus, yearBonus, score, r.title(), r.author(), r.year());

            if (score > bestScore + TIE_EPSILON) {
                bestScore = score;
                best = r;
                tie = false;
            } else if (Math.abs(score - bestScore) <= TIE_EPSILON) {
                tie = true;
            }
        }

        if (bestScore < TITLE_MATCH_THRESHOLD) {
            log.info("Sigla: лучшее совпадение для '{}' слишком слабое (score={}) — не возвращаю", query, bestScore);
            return null;
        }

        if (tie) {
            log.warn("Sigla: неоднозначность для '{}' — несколько записей с одинаковым score={}, " +
                    "не могу выбрать без дополнительных подсказок (автор/год) — не возвращаю", query, bestScore);
            return null;
        }

        return best;
    }

    private Set<String> normalizeWords(String s) {
        return new LinkedHashSet<>(Arrays.asList(
                s.toLowerCase().replaceAll("[^a-zа-яё0-9\\s]", " ").trim().split("\\s+")
        ));
    }

    private String stripElectronicResourceSuffix(String text) {
        int idx = text.indexOf("Электронный ресурс");
        return (idx > 0 ? text.substring(0, idx) : text).trim();
    }

    private String extractQueryParam(String url, String param) {
        Matcher m = Pattern.compile("[?&]" + param + "=([^&]*)").matcher(url);
        if (!m.find()) return null;
        return java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }

    private String extractCity(String cityPublisherYear) {
        int idx = cityPublisherYear.indexOf(" : ");
        return idx > 0 ? cityPublisherYear.substring(0, idx).trim() : null;
    }

    private String extractPublisher(String cityPublisherYear) {
        int colonIdx = cityPublisherYear.indexOf(" : ");
        if (colonIdx < 0) return null;
        String rest = cityPublisherYear.substring(colonIdx + 3);
        int lastComma = rest.lastIndexOf(',');
        return lastComma > 0 ? rest.substring(0, lastComma).trim() : rest.trim();
    }

    private String extractYear(String cityPublisherYear) {
        Matcher m = Pattern.compile("(\\d{4})").matcher(cityPublisherYear);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    public record SiglaRecord(String author, String title, String city, String publisher, String year, String isbn) {}
}