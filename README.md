# Book Metadata Service

Открытый бесплатный API для поиска метаданных книг по ISBN или названию — без
регистрации, без ключа. Объединяет Open Library, Google Books, FantLab и
другие источники в одну карточку, автоматически обогащая базу при первом
запросе по книге, которой ещё нет.

Публичный инстанс: **[bookmetadata.ru](https://bookmetadata.ru)** ·
[документация API](https://bookmetadata.ru/docs) ·
[Swagger](https://bookmetadata.ru/swagger-ui/index.html)

## Быстрый старт (без сборки, готовый образ с Docker Hub)

Нужны только два файла — не нужно клонировать репозиторий и ставить Java/Gradle:

```bash
curl -O https://raw.githubusercontent.com/malexple/book-metadata-service/main/docker-compose.hub.yml
curl -O https://raw.githubusercontent.com/malexple/book-metadata-service/main/.env.example
cp .env.example .env   # открыть и поправить DB_PASSWORD
docker compose -f docker-compose.hub.yml up -d
curl http://localhost:8080/api/v1/search?q=Дорога
```

Образ: [`malexple/book-metadata-service`](https://hub.docker.com/r/malexple/book-metadata-service)
на Docker Hub.

## Сборка из исходников (для разработки)

```bash
git clone https://github.com/malexple/book-metadata-service.git
cd book-metadata-service
cp .env.example .env
docker compose up -d --build
```

## API

Три публичных метода, ограничение 60 запросов/мин с одного IP:

```bash
curl "https://bookmetadata.ru/api/v1/search/isbn/9785389143852"
curl "https://bookmetadata.ru/api/v1/search/title?q=Мастер+и+Маргарита"
curl "https://bookmetadata.ru/api/v1/search?q=9785389143852"
```

Выбор полей: `?fields=Title,AuthorName,ISBN`. Полное описание — на
[странице документации](https://bookmetadata.ru/docs) и в
[Swagger UI](https://bookmetadata.ru/swagger-ui/index.html).

## Опциональные источники (выключены по умолчанию)

Некоторые источники обогащения выключены "из коробки" и требуют осознанного
включения — см. `.env.example`:

- **LibGen** (`LIBGEN_ENABLED`) — юридически серая зона.
- **Sigla / sigla.ru** (`SIGLA_ENABLED`) — неофициальный скрапинг сайта
  библиотеки МГУ, не предназначенного для автоматических обращений.
- **LLM fallback** (`LLM_FALLBACK_ENABLED`) — требует отдельно поднятые
  SearXNG и Ollama; результаты идут не напрямую в базу, а в очередь на
  ручную модерацию.

## Архитектура

EAV/pivot-модель метаданных (mtobjects/mtfields/mtdata) в духе white paper
Force.com Multitenant Architecture — гибкая схема без ALTER TABLE при
добавлении новых полей. PostgreSQL + Flyway, Spring Boot 3.4 / Java 21.

## Разработка и деплой

- `build-and-push.sh` / `build-and-push.ps1` — сборка и публикация образа
  в Docker Hub под тегами `:latest` и `:<git-sha>`.
- `docker-compose.yml` — сборка из исходников (для разработки).
- `docker-compose.hub.yml` — готовый образ с Docker Hub (для конечных
  пользователей, без клонирования репозитория).

## Лицензия

Apache 2.0