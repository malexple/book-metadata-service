package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtRelationship;

import java.util.List;
import java.util.UUID;

@Repository
public interface MtRelationshipRepository extends JpaRepository<MtRelationship, Long> {
    List<MtRelationship> findByOrgIdAndParentGuid(Integer orgId, UUID parentGuid);
    List<MtRelationship> findByOrgIdAndChildGuid(Integer orgId, UUID childGuid);
}