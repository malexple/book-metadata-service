(function () {
    'use strict';

    const form = document.getElementById('searchForm');
    const input = document.getElementById('queryInput');
    const btn = document.getElementById('searchBtn');
    const statusEl = document.getElementById('status');
    const resultsEl = document.getElementById('results');

    function setStatus(text, cssClass) {
        statusEl.textContent = text;
        statusEl.className = cssClass || '';
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) return '';
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;');
    }

    // Некоторые записи в базе сохранены со старой ссылкой на обложку по http://
    // (так отдавал Google Books API) - CSP img-src разрешает только https:,
    // и до тех пор, пока бэкенд не переобогатит эти записи, подчищаем схему
    // прямо на клиенте, чтобы не терять уже найденные обложки.
    function toHttps(url) {
        if (!url) return url;
        return url.startsWith('http://') ? 'https://' + url.slice('http://'.length) : url;
    }

    function renderBooks(books) {
        resultsEl.innerHTML = '';
        if (!Array.isArray(books) || books.length === 0) {
            setStatus('Ничего не найдено. Попробуйте изменить запрос.', 'muted');
            return;
        }
        setStatus('Найдено: ' + books.length, 'muted');
        for (const book of books) {
            const card = document.createElement('article');
            card.className = 'book-card';

            const coverUrl = toHttps(book.CoverUrl);
            if (coverUrl) {
                const img = document.createElement('img');
                img.src = coverUrl;
                img.alt = 'Обложка';
                img.loading = 'lazy';
                card.appendChild(img);
            }

            const title = document.createElement('h3');
            title.textContent = book.Title || '(без названия)';
            card.appendChild(title);

            const author = document.createElement('p');
            author.innerHTML = '<strong>Автор:</strong> ' + escapeHtml(book.AuthorName || '—');
            card.appendChild(author);

            const publisher = document.createElement('p');
            const yearPart = book.PublishedYear ? ', ' + escapeHtml(book.PublishedYear) : '';
            publisher.innerHTML = '<strong>Издательство:</strong> ' + escapeHtml(book.Publisher || '—') + yearPart;
            card.appendChild(publisher);

            const isbn = document.createElement('p');
            isbn.innerHTML = '<strong>ISBN:</strong> ' + escapeHtml(book.ISBN || '—');
            card.appendChild(isbn);

            resultsEl.appendChild(card);
        }
    }

    async function runSearch(query) {
        btn.disabled = true;
        resultsEl.innerHTML = '';
        setStatus('Ищем...', 'muted');

        let response;
        try {
            response = await fetch('/api/v1/search?q=' + encodeURIComponent(query));
        } catch (networkErr) {
            console.error('[search] network-level fetch failure:', networkErr);
            setStatus('Ошибка сети. Проверьте, запущен ли сервис, и повторите попытку.', 'error');
            btn.disabled = false;
            return;
        }

        if (response.status === 429) {
            setStatus('Слишком много запросов. Подождите немного и повторите поиск.', 'error');
            btn.disabled = false;
            return;
        }

        if (!response.ok) {
            let serverMessage = 'HTTP ' + response.status;
            try {
                const errBody = await response.json();
                if (errBody && errBody.message) serverMessage = errBody.message;
            } catch (parseErr) {
                console.warn('[search] error response was not JSON:', parseErr);
            }
            console.error('[search] server returned error status:', response.status, serverMessage);
            setStatus('Ошибка сервера: ' + serverMessage, 'error');
            btn.disabled = false;
            return;
        }

        let books;
        try {
            books = await response.json();
        } catch (parseErr) {
            console.error('[search] response was 200 but body is not valid JSON:', parseErr);
            setStatus('Сервер вернул некорректный ответ. Сообщите разработчику (см. консоль).', 'error');
            btn.disabled = false;
            return;
        }

        try {
            renderBooks(books);
        } catch (renderErr) {
            console.error('[search] rendering failed:', renderErr, 'raw books payload:', books);
            setStatus('Ошибка отображения результатов. Сообщите разработчику (см. консоль).', 'error');
        }

        btn.disabled = false;
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        const query = input.value.trim();
        if (query) runSearch(query);
    });
})();