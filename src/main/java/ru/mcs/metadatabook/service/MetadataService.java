package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.repository.MtFieldRepository;
import ru.mcs.metadatabook.repository.MtObjectRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Работа с метаданными объектов/полей. Является единственным местом,
 * где происходит маппинг "человеческое имя поля" -> "field_num" (слот в mt_data).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MetadataService {

    private final MtObjectRepository objectRepository;
    private final MtFieldRepository fieldRepository;

    public List<MtObject> getObjectsByOrg(Integer orgId) {
        return objectRepository.findByOrgId(orgId);
    }

    public MtObject getObjectByName(Integer orgId, String objName) {
        return objectRepository.findByOrgIdAndObjName(orgId, objName)
                .orElseThrow(() -> new IllegalArgumentException("Object not found: " + objName));
    }

    public List<MtField> getFieldsByObject(Integer orgId, Integer objId) {
        return fieldRepository.findByOrgIdAndObjId(orgId, objId);
    }

    public List<MtField> getIndexedFields(Integer orgId, Integer objId) {
        return fieldRepository.findByOrgIdAndObjIdAndIsIndexedTrue(orgId, objId);
    }

    /** fieldName -> MtField, для быстрого маппинга ?fields=title,isbn в слоты. */
    public Map<String, MtField> getFieldMapByName(Integer orgId, Integer objId) {
        return getFieldsByObject(orgId, objId).stream()
                .collect(Collectors.toMap(f -> f.getFieldName().toLowerCase(), f -> f));
    }

    /** fieldNum -> MtField, для построения ответа по значениям слотов. */
    public Map<Integer, MtField> getFieldMapBySlot(Integer orgId, Integer objId) {
        return getFieldsByObject(orgId, objId).stream()
                .collect(Collectors.toMap(MtField::getFieldNum, f -> f));
    }

    public MtField getFieldByName(Integer orgId, Integer objId, String fieldName) {
        return fieldRepository.findByOrgIdAndObjIdAndFieldName(orgId, objId, fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Field not found: " + fieldName));
    }
}