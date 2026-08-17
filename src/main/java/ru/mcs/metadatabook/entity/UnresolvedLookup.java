package ru.mcs.metadatabook.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Очередь книг, которые не удалось найти ни в собственной БД, ни у внешних
 * источников (Litres/Ozon). Сюда попадают записи на ручной разбор.
 */
@Entity
@Table(name = "unresolved_lookup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnresolvedLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "org_id", nullable = false)
    @Builder.Default
    private Integer orgId = 1;

    @Column(name = "isbn")
    private String isbn;

    @Column(name = "title")
    private String title;

    @Column(name = "author_name")
    private String authorName;

    @Type(StringArrayType.class)
    @Column(name = "attempted_sources", columnDefinition = "text[]")
    @Builder.Default
    private String[] attemptedSources = new String[0];

    @Column(name = "status")
    @Builder.Default
    private String status = "pending";

    @Type(JsonType.class)
    @Column(name = "raw_context", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> rawContext = new HashMap<>();

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}