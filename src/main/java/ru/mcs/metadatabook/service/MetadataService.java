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
                .orElseThrow(() -> new RuntimeException("Object not found: " + objName));
    }

    public List<MtField> getFieldsByObject(Integer orgId, Integer objId) {
        return fieldRepository.findByOrgIdAndObjId(orgId, objId);
    }

    public List<MtField> getIndexedFields(Integer orgId, Integer objId) {
        return fieldRepository.findByOrgIdAndObjIdAndIsIndexedTrue(orgId, objId);
    }

    public List<MtField> getUniqueFields(Integer orgId, Integer objId) {
        return fieldRepository.findByOrgIdAndObjIdAndIsUniqueTrue(orgId, objId);
    }

    public Map<String, MtField> getFieldMap(Integer orgId, Integer objId) {
        return getFieldsByObject(orgId, objId).stream()
                .collect(Collectors.toMap(MtField::getFieldName, f -> f));
    }

    public Map<Integer, MtField> getFieldMapBySlot(Integer orgId, Integer objId) {
        return getFieldsByObject(orgId, objId).stream()
                .collect(Collectors.toMap(MtField::getFieldNum, f -> f));
    }
}