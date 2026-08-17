package ru.mcs.metadatabook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.mcs.metadatabook.entity.MtAttachment;

import java.util.List;

@Repository
public interface MtAttachmentRepository extends JpaRepository<MtAttachment, Long> {
    List<MtAttachment> findByOrgIdAndOcrStatus(Integer orgId, String ocrStatus);
}