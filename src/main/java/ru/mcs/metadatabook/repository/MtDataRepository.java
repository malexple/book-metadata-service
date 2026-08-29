package ru.mcs.metadatabook.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtDataId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MtDataRepository extends JpaRepository<MtData, MtDataId> {

    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId AND d.isDeleted = false")
    Page<MtData> findByOrgIdAndObjId(@Param("orgId") Integer orgId, @Param("objId") Integer objId, Pageable pageable);

    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.id.guid = :guid AND d.isDeleted = false")
    MtData findByOrgIdAndGuid(@Param("orgId") Integer orgId, @Param("guid") UUID guid);

    @Modifying
    @Query("UPDATE MtData d SET d.isDeleted = true, d.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE d.id.orgId = :orgId AND d.id.guid = :guid")
    void softDelete(@Param("orgId") Integer orgId, @Param("guid") UUID guid);

    /**
     * Выборка записей по набору guid (например, найденных через mt_indexes при поиске).
     */
    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.id.guid IN :guids AND d.isDeleted = false")
    List<MtData> findByOrgIdAndGuidIn(@Param("orgId") Integer orgId, @Param("guids") List<UUID> guids);

    @Query("SELECT COUNT(d) FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId AND d.isDeleted = false")
    long countByOrgIdAndObjId(@Param("orgId") Integer orgId, @Param("objId") Integer objId);

    @Query("SELECT COUNT(d) FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId " +
            "AND d.isDeleted = false AND d.createdAt >= :since")
    long countByOrgIdAndObjIdAndCreatedAtAfter(@Param("orgId") Integer orgId, @Param("objId") Integer objId,
                                               @Param("since") OffsetDateTime since);
}