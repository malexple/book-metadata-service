package ru.mcs.metadatabook.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Обязательно для bucket4j-spring-boot-starter начиная с версии 0.12.3 (breaking change) -
 * без @EnableCaching стартер не находит именованные кэши из spring.cache.cache-names
 * и падает при старте с "No Bucket4j cache configuration found", даже если
 * bucket4j.cache-to-use указан корректно.
 */
@Configuration
@EnableCaching
public class BucketCacheConfig {
}