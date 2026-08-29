package ru.mcs.metadatabook.client;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * НЕ официальный API — HTML-скрапинг legacy JSP-приложения. Известные подводные камни
 * (все обнаружены эмпирически, см. историю дебага):
 *
 * 1. URL требует ВСЕХ параметров формы, включая пустые (иначе 500 crash.jsp).
 *    Не пытайтесь "почистить" query string — legacy JSP ожидает их присутствия.
 *
 * 2. URL передаётся в RestClient как готовый java.net.URI, а НЕ как String —
 *    при передаче String Spring прогоняет его через UriComponentsBuilder, который
 *    повторно percent-кодирует уже закодированную кириллицу (%D0%B0 -> %25D0%25B0),
 *    из-за чего поиск молча возвращает 0 записей.
 *
 * 3. Сервер не указывает charset в HTTP-заголовке Content-Type (хотя он есть
 *    в <meta> внутри HTML) — читаем тело как byte[] и декодируем вручную как UTF-8,
 *    иначе Spring откатится на ISO-8859-1 (HTTP-спека для text/* без charset) и
 *    кириллица превратится в mojibake. Внешне выглядит как "структура нашлась
 *    (N строк), но совпадений по тексту нет" — score=0.0 везде.
 *
 * 4. Ячейка с порядковым номером строки ("1.", "2.", ...) в разметке ТОЖЕ имеет
 *    class="table-text" — она попадает в td.table-text первым элементом (index 0),
 *    сдвигая индексы: cells.get(0)=номер, (1)=автор, (2)=заглавие, (3)=город/издательство/год.
 *
 * 5. Пагинация: table.jsp с psz=20/bs=20 отдаёт только первые 20 записей;
 *    для полного покрытия (напр. 24 из 24) нужен второй запрос с i=21 и т.д.
 *    Пока не реализовано — берём только первую страницу.
 *
 * 6. КЛЮЧЕВОЕ ОГРАНИЧЕНИЕ: родовые технические заглавия (напр. "англо-русский
 *    словарь по вычислительной технике") встречаются подстрокой в десятках разных
 *    изданий разных лет и авторов. Чистое текстовое сравнение НЕ различает их —
 *    приходило 20/20 совпадений с одинаковым score=1.0 по чистому recall.
 *    Поэтому здесь Jaccard (штрафует "лишние" слова в заглавии) + опциональные
 *    бонусы за совпадение автора/года (authorHint/yearHint). Если после учёта
 *    подсказок несколько записей всё ещё делят первое место — метод возвращает
 *    null (лучше не найти, чем подставить не ту книгу), а не берёт первую попавшуюся.
 *
 * cc=c — каталог "НБ МГУ, Электронный каталог Книг с 1990 г." — единственный
 * эмпирически подтверждённый код на данный момент.
 */
@Component
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

    private final RestClient restClient = RestClient.builder()
            .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; book-metadata-service/1.0)")
            .build();

    public SiglaRecord findByTitle(String title) {
        return findByTitle(title, null, null);
    }

    /**
     * @param authorHint известная фамилия/автор (из OCR или другого источника) — опционально,
     *                   но настоятельно рекомендуется для родовых заглавий (словари, справочники).
     * @param yearHint   известный год издания — опционально, помогает разрешить неоднозначность.
     */
    public SiglaRecord findByTitle(String title, String authorHint, String yearHint) {
        List<SiglaRecord> records = search(title);
        return pickBestMatch(title, authorHint, yearHint, records);
    }

    public List<SiglaRecord> search(String title) {
        List<SiglaRecord> results = new ArrayList<>();
        String rawUrl = BASE_URL + "?o=a&f=1016&t=3&v0="
                + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + STATIC_PARAMS;

        try {
            URI uri = URI.create(rawUrl);

            byte[] rawBody = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (rawBody == null) return results;
            String html = new String(rawBody, StandardCharsets.UTF_8);

            Document doc = Jsoup.parse(html);

            if (doc.title() != null && doc.title().contains("Серьезная ошибка")) {
                log.error("Sigla: сервер вернул crash.jsp для запроса '{}'", title);
                return results;
            }

            Elements rows = doc.select("tr.table-body");
            log.debug("Sigla: получено {} строк результатов для '{}'", rows.size(), title);

            for (Element row : rows) {
                // Индекс 0 в td.table-text — это порядковый номер строки ("1."), не данные.
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
                        (isbn == null || isbn.isBlank()) ? null : isbn
                ));
            }
        } catch (Exception e) {
            log.error("Sigla: ошибка запроса для '{}'", title, e);
        }

        return results;
    }

    /**
     * Скоринг: Jaccard-похожесть заглавия (штрафует "лишние" слова, не только
     * недостающие) + бонус за совпадение автора/года, если подсказки заданы.
     * При явной ничьей по итоговому скору — возвращает null.
     */
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