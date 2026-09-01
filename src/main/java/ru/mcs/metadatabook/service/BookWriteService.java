package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.repository.MtDataRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Создание/обогащение записи книги. Используется как ручным вводом,
 * так и будущим EnrichmentService (Litres/Ozon) и OcrIngestService.
 *
 * Контракт: values — Map<fieldName, value>, source — источник данных
 * ("manual", "litres", "ozon", "ocr"), confidence — опциональная уверенность (0..1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookWriteService {

    private final MetadataService metadataService;
    private final MtDataRepository dataRepository;
    private final PivotIndexService pivotIndexService;

    @Transactional
    public MtData createBook(Integer orgId, Map<String, String> values, String source, Double confidence) {
        MtObject bookObject = metadataService.getObjectByName(orgId, "Book");
        Map<String, MtField> fieldMap = metadataService.getFieldMapByName(orgId, bookObject.getObjId());

        MtData data = MtData.builder()
                .objId(bookObject.getObjId())
                .build();
        data.setOrgId(orgId);
        data.setGuid(UUID.randomUUID());

        applyValues(data, values, fieldMap, source, confidence);

        String title = values.get("Title") != null ? values.get("Title") : values.get("title");
        data.setName(title != null && title.length() > 1000 ? title.substring(0, 1000) : title);

        MtData saved = dataRepository.save(data);

        List<MtField> indexedFields = metadataService.getIndexedFields(orgId, bookObject.getObjId());
        pivotIndexService.syncIndexes(saved, indexedFields);

        return saved;
    }

    /**
     * Частичное обновление существующей записи. Пишет историю по каждому
     * реально изменившемуся полю и обновляет provenance.
     */
    @Transactional
    public MtData enrichBook(Integer orgId, UUID guid, Map<String, String> values, String source, Double confidence) {
        MtData data = dataRepository.findByOrgIdAndGuid(orgId, guid);
        if (data == null) {
            throw new IllegalArgumentException("Book not found: " + guid);
        }

        MtObject bookObject = metadataService.getObjectByName(orgId, "Book");
        Map<String, MtField> fieldMap = metadataService.getFieldMapByName(orgId, bookObject.getObjId());

        for (Map.Entry<String, String> entry : values.entrySet()) {
            MtField field = fieldMap.get(entry.getKey().toLowerCase());
            if (field == null) {
                log.warn("Unknown field '{}' during enrich, skipping", entry.getKey());
                continue;
            }
            pivotIndexService.recordHistoryIfChanged(data, field.getFieldNum(), entry.getValue(), field.getDataType());
            data.setValue(field.getFieldNum(), entry.getValue());
            data.putSource(field.getFieldNum(), source, confidence);
        }

        MtData saved = dataRepository.save(data);

        List<MtField> indexedFields = metadataService.getIndexedFields(orgId, bookObject.getObjId());
        pivotIndexService.syncIndexes(saved, indexedFields);

        return saved;
    }

    private void applyValues(MtData data, Map<String, String> values, Map<String, MtField> fieldMap,
                             String source, Double confidence) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            MtField field = fieldMap.get(entry.getKey().toLowerCase());
            if (field == null) {
                log.warn("Unknown field '{}' during create, skipping", entry.getKey());
                continue;
            }
            data.setValue(field.getFieldNum(), entry.getValue());
            data.putSource(field.getFieldNum(), source, confidence);
        }
    }
}