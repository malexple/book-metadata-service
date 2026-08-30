package ru.mcs.metadatabook.util;

/**
 * Валидация и нормализация ISBN-10/ISBN-13 по контрольной сумме.
 *
 * Нормализация (убрать дефисы/пробелы) нужна не ради экономии места в БД
 * (VARCHAR(4000) — variable-length, разница в 3-4 байта на строку ничтожна),
 * а ради корректной дедупликации: "978-5-389-14385-2" и "9785389143852" —
 * одна и та же книга, но как РАЗНЫЕ строки для persist()/findByIndexedField
 * они создают два независимых дубля в mt_data (см. баг с "Дорога" Маккарти).
 *
 * Проверка по контрольной сумме также защищает от лишних внешних HTTP-вызовов:
 * без нормализации+валидации любая случайная 10-13-значная строка уходила бы
 * в Open Library/Google Books/Sigla как "похоже на ISBN", тратя их квоту впустую.
 */
public final class IsbnValidator {

    private IsbnValidator() {}

    /** Убирает всё, кроме цифр и X/x, приводит X к верхнему регистру. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9Xx]", "").toUpperCase();
    }

    public static boolean isValid(String normalizedIsbn) {
        if (normalizedIsbn == null) return false;
        return switch (normalizedIsbn.length()) {
            case 10 -> isValidIsbn10(normalizedIsbn);
            case 13 -> isValidIsbn13(normalizedIsbn);
            default -> false;
        };
    }

    private static boolean isValidIsbn10(String isbn) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int digit;
            if (c == 'X') {
                if (i != 9) return false; // X допустим только последним символом
                digit = 10;
            } else if (Character.isDigit(c)) {
                digit = c - '0';
            } else {
                return false;
            }
            sum += digit * (10 - i);
        }
        return sum % 11 == 0;
    }

    private static boolean isValidIsbn13(String isbn) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (!Character.isDigit(c)) return false;
            int digit = c - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return sum % 10 == 0;
    }
}