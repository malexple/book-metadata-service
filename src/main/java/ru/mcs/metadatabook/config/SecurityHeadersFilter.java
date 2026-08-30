package ru.mcs.metadatabook.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Заголовки безопасности для наших собственных HTML-страниц (/, /search, /docs).
 *
 * CSP без 'unsafe-inline' работает только потому, что мы вынесли весь JS/CSS
 * в отдельные файлы (/js/*.js, /css/*.css) - без этого браузер сам заблокировал
 * бы inline <script>/<style>, а не только гипотетический корпоративный прокси.
 *
 * img-src разрешает любой https-хост, потому что обложки книг приходят с
 * разных источников обогащения (Open Library, Google Books, FantLab, LibGen) -
 * их конкретные CDN-домены заранее не перечислить и они могут смениться.
 *
 * Swagger UI и OpenAPI JSON намеренно исключены (shouldNotFilter) - библиотека
 * springdoc использует свои inline-ассеты, и ужесточённый CSP сломает саму
 * страницу Swagger, а не улучшит её безопасность (Swagger и так не отдаёт
 * пользовательский HTML, только фиксированный UI поверх нашего JSON).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/api-docs") || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer-when-downgrade");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self'; " +
                        "img-src 'self' https: data:; " +
                        "connect-src 'self'; " +
                        "base-uri 'self'; " +
                        "frame-ancestors 'none'");

        chain.doFilter(request, response);
    }
}