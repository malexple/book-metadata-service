package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtIndex;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.repository.MtDataRepository;
import ru.mcs.metadatabook.repository.MtIndexRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Динамический слой чтения книг для публичного API.
 * Отвечает за: 1) поиск по индексированным полям через mt_indexes,
 * 2) сборку ответа только по запрошенным полям (?fields=title,isbn,...).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookQueryService {

    private static final String BOOK_OBJECT_NAME = "Book";

    private final MetadataService metadataService;
    private final MtDataRepository dataRepository;
    private final MtIndexRepository indexRepository;

    /**
     * Разбирает ?fields=title,isbn,author в список MtField, валидируя имена
     * против реальной метаданных объекта. Пустой/null selectedFields -> все поля.
     */
    public List<MtField> resolveRequestedFields(Integer orgId, Integer objId, String selectedFields) {
        Map<String, MtField> byName = metadataService.getFieldMapByName(orgId, objId);

        if (selectedFields == null || selectedFields.isBlank()) {
            return List.copyOf(byName.values());
        }

        return java.util.Arrays.stream(selectedFields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name -> {
                    MtField field = byName.get(name.toLowerCase());
                    if (field == null) {
                        throw new IllegalArgumentException("Unknown field: " + name);
                    }
                    return field;
                })
                .collect(Collectors.toList());
    }

    public MtObject getBookObject(Integer orgId) {
        return metadataService.getObjectByName(orgId, BOOK_OBJECT_NAME);
    }

    /** Поиск по точному значению индексированного поля (например, ISBN). */
    public List<MtData> findByIndexedField(Integer orgId, Integer objId, String fieldName, String value) {
        MtField field = metadataService.getFieldByName(orgId, objId, fieldName);
        if (!Boolean.TRUE.equals(field.getIsIndexed())) {
            throw new IllegalArgumentException("Field is not indexed and cannot be searched: " + fieldName);
        }

        List<MtIndex> matches = indexRepository.findByStringValue(
                orgId, objId, field.getFieldNum(), value.toLowerCase().trim());

        List<UUID> guids = matches.stream().map(MtIndex::getGuid).toList();
        if (guids.isEmpty()) return List.of();

        return dataRepository.findByOrgIdAndGuidIn(orgId, guids);
    }

    public MtData findByGuid(Integer orgId, UUID guid) {
        return dataRepository.findByOrgIdAndGuid(orgId, guid);
    }

    /** Постраничная выборка без фильтра — все книги организации. */
    public List<MtData> findAll(Integer orgId, Integer objId, int page, int size) {
        return dataRepository.findByOrgIdAndObjId(orgId, objId, PageRequest.of(page, size)).getContent();
    }

    /** Собирает ответ по одной записи только с запрошенными полями. */
    public Map<String, Object> toResponseMap(MtData data, List<MtField> requestedFields) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("guid", data.getGuid());
        for (MtField field : requestedFields) {
            String rawValue = data.getValue(field.getFieldNum());
            result.put(field.getFieldName(), convert(rawValue, field.getDataType()));
        }
        return result;
    }

    private Object convert(String rawValue, String dataType) {
        if (rawValue == null) return null;
        return switch (dataType) {
            case "number" -> {
                try {
                    yield new java.math.BigDecimal(rawValue);
                } catch (NumberFormatException e) {
                    yield rawValue;
                }
            }
            default -> rawValue;
        };
    }

    public List<MtData> findByIndexedFieldContaining(Integer orgId, Integer objId, String fieldName, String value) {
        MtField field = metadataService.getFieldByName(orgId, objId, fieldName);
        if (!Boolean.TRUE.equals(field.getIsIndexed())) {
            throw new IllegalArgumentException("Field is not indexed and cannot be searched: " + fieldName);
        }
        String pattern = "%" + value.toLowerCase().trim() + "%";
        List<MtIndex> matches = indexRepository.findByStringValueContaining(
                orgId, objId, field.getFieldNum(), pattern);
        List<UUID> guids = matches.stream().map(MtIndex::getGuid).toList();
        if (guids.isEmpty()) return List.of();
        return dataRepository.findByOrgIdAndGuidIn(orgId, guids);
    }
}