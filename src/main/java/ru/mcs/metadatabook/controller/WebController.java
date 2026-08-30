package ru.mcs.metadatabook.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Обычный MVC-контроллер (не REST) — отдаёт Thymeleaf-шаблоны для сайта.
 * Никакой бизнес-логики здесь нет: вся работа с данными идёт на клиенте
 * через fetch() к /api/v1/public/search и /api/v1/public/stats — эти
 * эндпоинты уже rate-limited и не требуют apiKey от посетителя.
 */
@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/search")
    public String search() {
        return "search";
    }
}