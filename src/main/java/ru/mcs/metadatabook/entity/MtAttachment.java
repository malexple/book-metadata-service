package ru.mcs.metadatabook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Загруженные пользователем файлы (PDF/DjVu) для OCR-конвейера (Tesseract).
 */
@Entity
@Table(name = "mt_attachment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MtAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "org_id", nullable = false)
    @Builder.Default
    private Integer orgId = 1;

    @Column(name = "obj_id", nullable = false)
    private Integer objId;

    @Column(name = "guid")
    private UUID guid;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_format", nullable = false)
    private String fileFormat;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "ocr_status")
    @Builder.Default
    private String ocrStatus = "pending";

    @Column(name = "ocr_error")
    private String ocrError;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}