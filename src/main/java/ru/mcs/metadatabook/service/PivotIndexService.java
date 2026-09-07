package ru.mcs.metadatabook.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtFieldHistory;
import ru.mcs.metadatabook.entity.MtIndex;
import ru.mcs.metadatabook.repository.MtIndexRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Синхронизация типизированных индексов (mt_indexes) и истории изменений (mt_field_history)
 * при записи/обновлении mt_data. Вызывается из BookWriteService после каждого сохранения.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PivotIndexService {

    @PersistenceContext
    private EntityManager entityManager;

    private final MtIndexRepository indexRepository;

    private static final int MAX_INDEX_STRING_VALUE = 1000;

    @Transactional
    public void syncIndexes(MtData data, List<MtField> indexedFields) {
        Integer orgId = data.getOrgId();
        UUID guid = data.getGuid();

        indexRepository.deleteByOrgIdAndGuid(orgId, guid);

        for (MtField field : indexedFields) {
            String value = data.getValue(field.getFieldNum());
            if (value == null || value.isBlank()) continue;

            MtIndex index = MtIndex.builder()
                    .orgId(orgId)
                    .objId(data.getObjId())
                    .fieldNum(field.getFieldNum())
                    .guid(guid)
                    .build();

            populateIndexValue(index, field, value);
            entityManager.persist(index);
        }
    }

    /**
     * Пишет запись в историю, если значение слота реально изменилось.
     * Вызывать ДО применения нового значения в MtData (нужен oldValue).
     */
    @Transactional
    public void recordHistoryIfChanged(MtData data, int fieldNum, String newValue, String dataType) {
        String oldValue = data.getValue(fieldNum);
        if (java.util.Objects.equals(oldValue, newValue)) {
            return;
        }

        MtFieldHistory history = MtFieldHistory.builder()
                .orgId(data.getOrgId())
                .objId(data.getObjId())
                .guid(data.getGuid())
                .fieldNum(fieldNum)
                .oldValue(oldValue)
                .newValue(newValue)
                .dataType(dataType)
                .changedAt(OffsetDateTime.now())
                .build();

        entityManager.persist(history);
    }

    private void populateIndexValue(MtIndex index, MtField field, String value) {
        switch (field.getDataType()) {
            case "text", "email", "url", "phone", "picklist", "lookup" ->
                    index.setStringValue(truncateForIndex(value.toLowerCase().trim(), field.getLength()));
            case "number", "currency", "percent" -> {
                try {
                    index.setNumValue(new BigDecimal(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid number value for index: {}", value);
                }
            }
            case "date", "datetime" -> {
                try {
                    index.setDateValue(OffsetDateTime.parse(value));
                } catch (Exception e) {
                    log.warn("Invalid date value for index: {}", value);
                }
            }
            default -> index.setStringValue(truncateForIndex(value.toLowerCase().trim(), field.getLength()));
        }
    }

    private String truncateForIndex(String value, Integer declaredLength) {
        int limit = (declaredLength != null && declaredLength <= MAX_INDEX_STRING_VALUE)
                ? declaredLength : MAX_INDEX_STRING_VALUE;
        return value.length() > limit ? value.substring(0, limit) : value;
    }
}