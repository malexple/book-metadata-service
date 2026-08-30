(function () {
    'use strict';
    fetch('/api/v1/public/stats')
        .then(r => r.json())
        .then(data => {
            document.getElementById('stat-total').textContent = data.totalBooks.toLocaleString('ru-RU');
            document.getElementById('stat-today').textContent = data.booksAddedToday.toLocaleString('ru-RU');
            document.getElementById('stat-authors').textContent = data.authorsVerified.toLocaleString('ru-RU');
            document.getElementById('stat-requests').textContent = data.requestsToday.toLocaleString('ru-RU');
        })
        .catch(() => {
            // Тихо игнорируем — если стата ещё не насчиталась (первый час после запуска),
            // просто оставляем прочерки, не пугаем пользователя ошибкой на главной.
        });
})();