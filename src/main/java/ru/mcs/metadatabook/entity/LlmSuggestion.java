package ru.mcs.metadatabook.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Предложение метаданных от LLM fallback источника, ожидающее ручной модерации.
 * НЕ попадает в mt_data напрямую — только после явного approve через
 * EnrichmentController. См. комментарий в V6__llm_suggestions.sql.
 */
@Entity
@Table(name = "llm_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "org_id", nullable = false)
    @Builder.Default
    private Integer orgId = 1;

    @Column(name = "isbn")
    private String isbn;

    @Column(name = "query_title")
    private String queryTitle;

    @Type(JsonType.class)
    @Column(name = "suggested_values", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> suggestedValues = new HashMap<>();

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "status")
    @Builder.Default
    private String status = "pending";

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "resulting_guid")
    private UUID resultingGuid;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}