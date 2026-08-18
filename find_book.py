import json, re, requests, sys

OLLAMA_API = "http://localhost:11434/api/chat"
SEARXNG_URL = "http://localhost:8080/search"
MODEL = "llama3.2:3b"

PLACEHOLDER_VALUES = {
    "не указано", "none", "n/a", "неизвестно", "нет данных", "",
    "not specified", "unknown", "null", "не найдено", "no data",
}

VERIFIABLE_FIELDS = ["author", "year", "publisher", "isbn"]


def normalize(value):
    if isinstance(value, str) and value.strip().lower() in PLACEHOLDER_VALUES:
        return None
    return value


def normalize_record(record: dict) -> dict:
    return {k: normalize(v) for k, v in record.items()}


def web_search(query: str):
    r = requests.get(
        SEARXNG_URL,
        params={"q": query, "format": "json", "language": "ru"},
        timeout=15,
    )
    results = r.json().get("results", [])[:5]
    return results


def format_results_for_model(results) -> str:
    if not results:
        return "Ничего не найдено."
    return "\n\n".join(
        f"[{i+1}] {x.get('title','')}\n{x.get('content','')}\nURL: {x.get('url','')}"
        for i, x in enumerate(results)
    )


def find_matching_result(source_url, results):
    for r in results:
        if r.get("url") == source_url:
            return r
    return None


def normalize_text(s) -> str:
    return re.sub(r"\s+", " ", str(s)).strip().lower()


def field_confirmed(value, snippet_text) -> bool:
    if value is None:
        return True
    value_norm = normalize_text(value)
    if not value_norm:
        return True
    return value_norm in snippet_text


def verify_record(record: dict, results: list) -> dict:
    source_url = record.get("source_url")
    match = find_matching_result(source_url, results)

    if match is None:
        print(f"ПРЕДУПРЕЖДЕНИЕ: source_url '{source_url}' не найден дословно ни в одном результате поиска.")
        return {**{k: None for k in record}, "title": record.get("title")}

    snippet_text = normalize_text(match.get("title", "") + " " + match.get("content", ""))

    verified = dict(record)
    for field in VERIFIABLE_FIELDS:
        if not field_confirmed(record.get(field), snippet_text):
            print(f"ПРЕДУПРЕЖДЕНИЕ: поле '{field}' = '{record.get(field)}' не подтверждено текстом источника — обнулено.")
            verified[field] = None

    return verified


def stream_response(messages, tools=None, force_json=False):
    payload = {"model": MODEL, "messages": messages, "stream": True}
    if tools:
        payload["tools"] = tools
    if force_json:
        payload["format"] = "json"

    tool_calls = []
    final_content = ""

    with requests.post(OLLAMA_API, json=payload, stream=True, timeout=180) as r:
        if r.status_code != 200:
            print("HTTP ERROR:", r.status_code, r.text)
            return tool_calls, final_content

        for line in r.iter_lines():
            if not line:
                continue
            chunk = json.loads(line)
            if "error" in chunk:
                print("OLLAMA ERROR:", chunk["error"])
                return tool_calls, final_content

            msg = chunk.get("message", {})
            if msg.get("tool_calls"):
                tool_calls.extend(msg["tool_calls"])
            if msg.get("content"):
                final_content += msg["content"]
            if chunk.get("done"):
                break

    return tool_calls, final_content


def find_book(title: str):
    tools = [
        {
            "type": "function",
            "function": {
                "name": "web_search",
                "description": "Поиск метаданных книги в интернете",
                "parameters": {
                    "type": "object",
                    "properties": {"query": {"type": "string"}},
                    "required": ["query"],
                },
            },
        }
    ]

    system = (
        "Используй ТОЛЬКО данные из результатов web_search. "
        "Не используй собственные знания о книгах. "
        "Ответ строго в формате JSON со следующими полями: "
        "title (строка), author (строка или null), year (число или null), "
        "publisher (строка или null), isbn (строка или null), source_url (строка или null). "
        "ЗАПРЕЩЕНО использовать строки 'не указано', 'None', 'неизвестно', "
        "'not specified', 'unknown' или подобные — если данных нет, используй JSON null. "
        "source_url ОБЯЗАН быть скопирован дословно из результатов поиска. "
        "Заполняй author/year/publisher/isbn ТОЛЬКО если это значение буквально "
        "присутствует в тексте выбранного результата — иначе null. "
        "Не добавляй никакого текста вне JSON-объекта."
    )

    messages = [
        {"role": "system", "content": system},
        {"role": "user", "content": f"Найди метаданные книги: {title}"},
    ]

    print(f"\n--- Поиск книги: {title} ---\n")

    tool_calls, _ = stream_response(messages, tools=tools)

    if not tool_calls:
        print("Модель не вызвала поиск — книга не найдена или инструмент проигнорирован.")
        return None

    messages.append({"role": "assistant", "content": "", "tool_calls": tool_calls})

    all_results = []
    for tc in tool_calls:
        query = tc["function"]["arguments"].get("query", title)
        results = web_search(query)
        all_results.extend(results)
        formatted = format_results_for_model(results)

        print("=== SEARXNG QUERY ===")
        print(query)
        print("=== SEARXNG RESULT ===")
        print(formatted)
        print("======================\n")

        messages.append({"role": "tool", "content": formatted})

    _, final_content = stream_response(messages, force_json=True)

    if not final_content.strip():
        print("Пустой ответ модели.")
        return None

    try:
        record = json.loads(final_content)
    except json.JSONDecodeError:
        print("Не удалось распарсить JSON от модели:")
        print(final_content)
        return None

    record = normalize_record(record)
    record = verify_record(record, all_results)
    record = normalize_record(record)

    print("=== ИТОГОВАЯ ЗАПИСЬ (после верификации) ===")
    print(json.dumps(record, ensure_ascii=False, indent=2))
    return record


if __name__ == "__main__":
    query = sys.argv[1] if len(sys.argv) > 1 else "тестовая книга без обложки"
    find_book(query)