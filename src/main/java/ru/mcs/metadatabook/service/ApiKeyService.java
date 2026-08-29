package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.ApiKey;
import ru.mcs.metadatabook.entity.ApiKeyScope;
import ru.mcs.metadatabook.repository.ApiKeyRepository;

/**
 * Резолвинг orgId по API-ключу из apikeys + проверка уровня доступа (scope).
 *
 * Проверка НЕ через Spring Security (@PreAuthorize и т.п.) — это осознанное решение:
 * добавлять весь фреймворк ради трёх уровней доступа избыточно для текущего масштаба
 * сервиса, а ApiKeyService и так уже единственная точка входа для проверки ключа
 * во всех контроллерах. Если позже понадобится более сложная модель прав —
 * тогда и будет смысл вводить Spring Security.
 *
 * Сравнение уровней идёт через ApiKeyScope.ordinal() — ключ с более высоким scope
 * может делать всё, что доступно более низким (ADMIN проходит проверку PUBLIC_READ,
 * но не наоборот). См. порядок констант в самом enum.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    /**
     * Старое поведение (без проверки scope) — оставлено для обратной совместимости
     * там, где явная проверка не нужна (либо уже сделана вызывающим кодом).
     * Для новых вызовов используйте resolveOrgIdOrThrow(apiKey, requiredScope).
     */
    public Integer resolveOrgIdOrThrow(String apiKey) {
        return resolveKey(apiKey).getOrgId();
    }

    /**
     * @param requiredScope минимальный необходимый уровень доступа для операции.
     * @throws SecurityException если ключ невалиден/неактивен, либо его scope ниже требуемого.
     */
    public Integer resolveOrgIdOrThrow(String apiKey, ApiKeyScope requiredScope) {
        ApiKey key = resolveKey(apiKey);
        ApiKeyScope actualScope = ApiKeyScope.valueOf(key.getScope());

        if (actualScope.ordinal() < requiredScope.ordinal()) {
            throw new SecurityException(
                    "apiKey scope '" + actualScope + "' is insufficient, requires at least '" + requiredScope + "'");
        }
        return key.getOrgId();
    }

    private ApiKey resolveKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SecurityException("apiKey is required");
        }
        return apiKeyRepository.findByKeyValueAndIsActiveTrue(apiKey)
                .orElseThrow(() -> new SecurityException("Invalid or inactive apiKey"));
    }
}