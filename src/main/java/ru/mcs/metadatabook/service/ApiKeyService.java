package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.ApiKey;
import ru.mcs.metadatabook.repository.ApiKeyRepository;

/**
 * Проверка API-ключа для публичного /api/v1/books. Минимальная реализация:
 * ключ хранится в таблице api_keys (см. V4 миграцию), без хеширования и rate-limit.
 * Ужесточить перед реальным внешним использованием.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public Integer resolveOrgIdOrThrow(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SecurityException("apiKey is required");
        }
        ApiKey key = apiKeyRepository.findByKeyValueAndIsActiveTrue(apiKey)
                .orElseThrow(() -> new SecurityException("Invalid or inactive apiKey"));
        return key.getOrgId();
    }
}