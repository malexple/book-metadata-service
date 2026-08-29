package ru.mcs.metadatabook.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой счётчик запросов к публичному поиску для дашборда ("Запросов за сегодня: Z").
 * Намеренно НЕ использует Micrometer/Actuator metrics — тот уже считает http.server.requests,
 * но вытаскивать оттуда агрегат по конкретному URI сложнее, чем один AtomicLong.
 * Сбрасывается DailyStatsScheduler после каждого снапшота (см. resetAndGet()).
 */
@Component
public class RequestCounterFilter extends OncePerRequestFilter {

    private final AtomicLong publicRequestCount = new AtomicLong(0);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/v1/public/")) {
            publicRequestCount.incrementAndGet();
        }
        chain.doFilter(request, response);
    }

    /** Текущее значение без сброса — используется, если снапшот берётся чаще, чем раз в сутки. */
    public long getCurrent() {
        return publicRequestCount.get();
    }

    /**
     * Возвращает значение и обнуляет счётчик. Вызывается планировщиком раз в сутки
     * (в полночь) — если ловить его каждый час, придётся накапливать в самом
     * DailyStatsScheduler, а не сбрасывать тут (см. комментарий в шедулере).
     */
    public long resetAndGet() {
        return publicRequestCount.getAndSet(0);
    }
}