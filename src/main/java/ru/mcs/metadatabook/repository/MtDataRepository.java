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

import java.util.List;
import java.util.UUID;

@Repository
public interface MtDataRepository extends JpaRepository<MtData, MtDataId> {

    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId AND d.isDeleted = false")
    List<MtData> findByOrgIdAndObjId(@Param("orgId") Integer orgId, @Param("objId") Integer objId);

    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId AND d.isDeleted = false")
    Page<MtData> findByOrgIdAndObjIdPaged(@Param("orgId") Integer orgId, @Param("objId") Integer objId, Pageable pageable);

    @Query("SELECT d FROM MtData d WHERE d.id.orgId = :orgId AND d.id.guid = :guid AND d.isDeleted = false")
    MtData findByOrgIdAndGuid(@Param("orgId") Integer orgId, @Param("guid") UUID guid);

    @Query("SELECT COUNT(d) FROM MtData d WHERE d.id.orgId = :orgId AND d.objId = :objId AND d.isDeleted = false")
    long countByOrgIdAndObjId(@Param("orgId") Integer orgId, @Param("objId") Integer objId);

    @Modifying
    @Query("UPDATE MtData d SET d.isDeleted = true, d.deletedAt = CURRENT_TIMESTAMP WHERE d.id.orgId = :orgId AND d.id.guid = :guid")
    void softDelete(@Param("orgId") Integer orgId, @Param("guid") UUID guid);

    @Query(value = """
    SELECT org_id, guid, obj_id, name, value0, value1, value2, value3, value4 
    FROM mt_data 
    WHERE org_id = :orgId AND obj_id = :objId AND is_deleted = false 
    LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findLightweight(@Param("orgId") Integer orgId,
                                   @Param("objId") Integer objId,
                                   @Param("limit") int limit);
}
