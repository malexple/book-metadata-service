package ru.mcs.metadatabook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sigla (НБ МГУ) — неофициальный HTML-скрапинг публичного библиотечного портала
 * sigla.ru, а не документированный API. По умолчанию выключено (opt-in) — по той
 * же логике, что и LibGen: используете на свой страх и риск, осознанно включая.
 *
 * Дополнительно: min-request-interval-ms задаёт собственный троттлинг — минимальный
 * интервал между запросами К ЭТОМУ КОНКРЕТНОМУ сайту (не путать с Bucket4j, который
 * защищает НАШ сервер от внешних пользователей). Это уважение к чужой инфраструктуре:
 * sigla.ru — публичный сервис для читателей, а не выделенный API с известной пропускной
 * способностью, поэтому нагрузку на него стоит держать минимальной independent от того,
 * сколько людей одновременно ищут книги через наш сервис.
 */
@Data
@ConfigurationProperties(prefix = "sigla")
public class SiglaProperties {
    private boolean enabled = false;
    private long minRequestIntervalMs = 2000; // не чаще 1 запроса в 2 секунды
}