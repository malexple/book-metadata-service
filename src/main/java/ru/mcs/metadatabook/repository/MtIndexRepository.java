package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtIndex;

import java.util.List;
import java.util.UUID;

@Repository
public interface MtIndexRepository extends JpaRepository<MtIndex, Long> {

    @Query("SELECT i FROM MtIndex i WHERE i.orgId = :orgId AND i.objId = :objId " +
            "AND i.fieldNum = :fieldNum AND i.stringValue = :value")
    List<MtIndex> findByStringValue(@Param("orgId") Integer orgId, @Param("objId") Integer objId,
                                    @Param("fieldNum") Integer fieldNum, @Param("value") String value);

    @Query("SELECT i FROM MtIndex i WHERE i.orgId = :orgId AND i.objId = :objId " +
            "AND i.fieldNum = :fieldNum AND i.stringValue LIKE :pattern")
    List<MtIndex> findByStringValueContaining(@Param("orgId") Integer orgId, @Param("objId") Integer objId,
                                              @Param("fieldNum") Integer fieldNum, @Param("pattern") String pattern);

    List<MtIndex> findByOrgIdAndGuid(Integer orgId, UUID guid);

    @Modifying
    @Query("DELETE FROM MtIndex i WHERE i.orgId = :orgId AND i.guid = :guid")
    void deleteByOrgIdAndGuid(@Param("orgId") Integer orgId, @Param("guid") UUID guid);
}