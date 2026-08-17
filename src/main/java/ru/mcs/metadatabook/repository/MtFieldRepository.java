package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtField;

import java.util.List;
import java.util.Optional;

@Repository
public interface MtFieldRepository extends JpaRepository<MtField, Integer> {
    List<MtField> findByOrgIdAndObjId(Integer orgId, Integer objId);
    List<MtField> findByOrgIdAndObjIdAndIsIndexedTrue(Integer orgId, Integer objId);
    List<MtField> findByOrgIdAndObjIdAndIsUniqueTrue(Integer orgId, Integer objId);
    Optional<MtField> findByOrgIdAndObjIdAndFieldName(Integer orgId, Integer objId, String fieldName);
}