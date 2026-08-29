package ru.mcs.metadatabook.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "daily_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "org_id", nullable = false)
    @Builder.Default
    private Integer orgId = 1;

    @Column(name = "snapshot_at", nullable = false)
    @Builder.Default
    private OffsetDateTime snapshotAt = OffsetDateTime.now();

    @Column(name = "total_books", nullable = false)
    private Long totalBooks;

    @Column(name = "books_added_today", nullable = false)
    private Long booksAddedToday;

    @Column(name = "authors_verified", nullable = false)
    @Builder.Default
    private Long authorsVerified = 0L;

    @Column(name = "requests_today", nullable = false)
    @Builder.Default
    private Long requestsToday = 0L;
}