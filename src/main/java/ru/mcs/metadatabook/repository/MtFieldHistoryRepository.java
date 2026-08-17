package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtFieldHistory;

import java.util.List;
import java.util.UUID;

@Repository
public interface MtFieldHistoryRepository extends JpaRepository<MtFieldHistory, Long> {
    List<MtFieldHistory> findByOrgIdAndGuidOrderByChangedAtDesc(Integer orgId, UUID guid);
}