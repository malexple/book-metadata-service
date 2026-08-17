package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtFallbackIndex;
import ru.mcs.metadatabook.entity.MtIndex;
import ru.mcs.metadatabook.entity.MtNameDenorm;
import ru.mcs.metadatabook.entity.MtRelationship;
import ru.mcs.metadatabook.repository.MtIndexRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PivotIndexService {

    @PersistenceContext
    private EntityManager entityManager;

    private final MetadataService metadataService;
    private final MtIndexRepository indexRepository;

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

            populateIndexValue(index, field.getDataType(), value);
            entityManager.persist(index);
        }
    }

    @Transactional
    public void syncFallbackIndex(MtData data) {
        MtFallbackIndex fallback = MtFallbackIndex.builder()
                .id(new MtFallbackIndex.MtFallbackIndexId(data.getOrgId(), data.getGuid()))
                .objId(data.getObjId())
                .name(data.getName())
                .nameLower(data.getName() != null ? data.getName().toLowerCase().trim() : null)
                .modifiedAt(OffsetDateTime.now())
                .build();
        entityManager.merge(fallback);
    }

    @Transactional
    public void syncNameDenorm(MtData data) {
        MtNameDenorm denorm = MtNameDenorm.builder()
                .id(new MtNameDenorm.MtNameDenormId(data.getOrgId(), data.getGuid()))
                .objId(data.getObjId())
                .name(data.getName())
                .build();
        entityManager.merge(denorm);
    }

    @Transactional
    public void syncRelationship(MtData childData, MtField lookupField, UUID parentGuid, Integer parentObjId) {
        MtRelationship rel = MtRelationship.builder()
                .orgId(childData.getOrgId())
                .relationshipId(lookupField.getFieldId())
                .parentObjId(parentObjId)
                .parentGuid(parentGuid)
                .childObjId(childData.getObjId())
                .childGuid(childData.getGuid())
                .build();
        entityManager.persist(rel);
    }

    private void populateIndexValue(MtIndex index, String dataType, String value) {
        switch (dataType) {
            case "text", "email", "url", "phone", "picklist" ->
                    index.setStringValue(value.toLowerCase().trim());
            case "number", "currency", "percent" -> {
                try {
                    index.setNumValue(new BigDecimal(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid number value: {}", value);
                }
            }
            case "date", "datetime" -> {
                try {
                    index.setDateValue(OffsetDateTime.parse(value));
                } catch (Exception e) {
                    log.warn("Invalid date value: {}", value);
                }
            }
            default -> index.setStringValue(value.toLowerCase().trim());
        }
    }
}