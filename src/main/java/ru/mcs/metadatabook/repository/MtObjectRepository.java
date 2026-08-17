package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtObject;

import java.util.List;
import java.util.Optional;

@Repository
public interface MtObjectRepository extends JpaRepository<MtObject, Integer> {
    List<MtObject> findByOrgId(Integer orgId);
    Optional<MtObject> findByOrgIdAndObjName(Integer orgId, String objName);
}