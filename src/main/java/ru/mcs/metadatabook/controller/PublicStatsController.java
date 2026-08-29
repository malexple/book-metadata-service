package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mcs.metadatabook.entity.DailyStats;
import ru.mcs.metadatabook.repository.DailyStatsRepository;

import java.util.Map;

/**
 * 3-4 маркетинговых счётчика на главную страницу. Читает последний часовой
 * снапшот из daily_stats — никаких живых агрегатных запросов к mt_data здесь нет,
 * это единственный по-настоящему анонимный (без apiKey вообще) эндпоинт сервиса,
 * поэтому он должен быть максимально дешёвым для сервера при любой частоте вызова.
 * Дополнительно всё равно защищён тем же Bucket4j-правилом на /api/v1/public/*.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public Stats", description = "Публичные счётчики для главной страницы")
public class PublicStatsController {

    private static final int DEFAULT_ORG_ID = 1;

    private final DailyStatsRepository dailyStatsRepository;

    @Operation(summary = "Публичные агрегированные счётчики (обновляются раз в час)")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPublicStats() {
        DailyStats latest = dailyStatsRepository.findTopByOrgIdOrderBySnapshotAtDesc(DEFAULT_ORG_ID)
                .orElse(DailyStats.builder().totalBooks(0L).booksAddedToday(0L).authorsVerified(0L).requestsToday(0L).build());

        return ResponseEntity.ok(Map.of(
                "totalBooks", latest.getTotalBooks(),
                "booksAddedToday", latest.getBooksAddedToday(),
                "authorsVerified", latest.getAuthorsVerified(),
                "requestsToday", latest.getRequestsToday(),
                "updatedAt", latest.getSnapshotAt()
        ));
    }
}