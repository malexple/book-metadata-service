package ru.mcs.metadatabook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS только для публичных read-only эндпоинтов (/api/v1/search/**, /api/v1/public/**).
 *
 * allowedOrigins("*") тут осознанно и БЕЗОПАСНО, а не халатность: это анонимный
 * GET-only API без apiKey, без cookie/сессионной аутентификации и без побочных
 * эффектов, видимых конкретному пользователю (обогащение - общий для всех кэш,
 * а не персональное состояние). Разрешать любому браузерному источнику читать
 * этот API - ровно то, для чего он создан (плагины, сторонние сайты).
 *
 * НЕ применяется к /swagger-ui/**, /api-docs, /, /search, /docs - им кросс-доменный
 * доступ не нужен, дефолтная same-origin политика браузера их и так не трогает.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/search/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .maxAge(3600);

        registry.addMapping("/api/v1/public/**")
                .allowedOrigins("*")
                .allowedMethods("GET")
                .maxAge(3600);
    }
}