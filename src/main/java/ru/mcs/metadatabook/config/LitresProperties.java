package ru.mcs.metadatabook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "litres")
public class LitresProperties {

    private String baseUrl = "https://catalit.litres.ru/catalitv2";

    /**
     * ID приложения и секретный ключ партнёра. Для r_search_arts не обязательны
     * (доступен любому пользователю), но без них сервер жёстче лимитирует частоту
     * запросов. См. https://docs.litres.ru/public/6424300.html — "Подпись и
     * ограничение по частоте запросов".
     */
    private String appId;
    private String secretKey;

    private boolean enabled = true;

    /** Не чаще 1 запроса в секунду — так рекомендует сама документация Litres. */
    private long minRequestIntervalMs = 1100;
}