package ru.mcs.metadatabook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mcs.metadatabook.config.RequestCounterFilter;
import ru.mcs.metadatabook.entity.DailyStats;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.repository.DailyStatsRepository;
import ru.mcs.metadatabook.repository.MtDataRepository;
import ru.mcs.metadatabook.repository.MtIndexRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Раз в час пересчитывает публичные счётчики и сохраняет снапшот в daily_stats.
 * Публичный эндпоинт статистики читает ПОСЛЕДНИЙ снапшот, а не считает вживую -
 * см. обсуждение: живой COUNT(*) на анонимном эндпоинте - точка для DoS.
 *
 * requests_today берётся из RequestCounterFilter БЕЗ сброса (resetAndGet вызывается
 * отдельно, раз в сутки в полночь) - иначе почасовые снапшоты обнуляли бы друг друга
 * и в статистике "за сегодня" была бы видна только последняя пройденная минута.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyStatsScheduler {

    private static final int DEFAULT_ORG_ID = 1; // TODO: если появится настоящая мультитенантность на публичном сайте - параметризовать

    private final DailyStatsRepository dailyStatsRepository;
    private final MtDataRepository mtDataRepository;
    private final MtIndexRepository mtIndexRepository;
    private final MetadataService metadataService;
    private final RequestCounterFilter requestCounterFilter;

    @Scheduled(cron = "0 0 * * * *") // каждый час, в начале часа
    @Transactional
    public void takeSnapshot() {
        MtObject book = metadataService.getObjectByName(DEFAULT_ORG_ID, "Book");
        OffsetDateTime startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);

        long totalBooks = mtDataRepository.countByOrgIdAndObjId(DEFAULT_ORG_ID, book.getObjId());
        long booksToday = mtDataRepository.countByOrgIdAndObjIdAndCreatedAtAfter(DEFAULT_ORG_ID, book.getObjId(), startOfDay);

        long authorsVerified = 0;
        try {
            var authorNameField = metadataService.getFieldByName(DEFAULT_ORG_ID, book.getObjId(), "AuthorName");
            authorsVerified = mtIndexRepository.countDistinctStringValue(DEFAULT_ORG_ID, book.getObjId(), authorNameField.getFieldNum());
        } catch (IllegalArgumentException e) {
            log.warn("AuthorName field not found for stats, skipping authorsVerified");
        }

        DailyStats snapshot = DailyStats.builder()
                .orgId(DEFAULT_ORG_ID)
                .totalBooks(totalBooks)
                .booksAddedToday(booksToday)
                .authorsVerified(authorsVerified)
                .requestsToday(requestCounterFilter.getCurrent())
                .build();

        dailyStatsRepository.save(snapshot);
        log.info("Daily stats snapshot: totalBooks={} booksToday={} authorsVerified={} requestsToday={}",
                totalBooks, booksToday, authorsVerified, snapshot.getRequestsToday());
    }

    @Scheduled(cron = "0 0 0 * * *") // раз в сутки, в полночь UTC
    public void resetDailyRequestCounter() {
        long resetValue = requestCounterFilter.resetAndGet();
        log.info("Daily request counter reset, was {}", resetValue);
    }
}