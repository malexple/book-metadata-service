package ru.mcs.metadatabook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "google.books")
public class GoogleBooksProperties {

    /**
     * Без ключа общая анонимная квота Google Books API на всех пользователей мира
     * может быть исчерпана (наблюдалось на практике: quota_limit_value=0).
     * Получить бесплатно: console.cloud.google.com -> создать проект ->
     * включить "Books API" -> Credentials -> Create API key. Оплата карты не требуется
     * для базовой дневной квоты.
     */
    private String apiKey;
}