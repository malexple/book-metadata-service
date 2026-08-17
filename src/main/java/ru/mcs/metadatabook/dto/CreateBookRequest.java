package ru.mcs.metadatabook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Запрос на создание/обогащение книги")
public class CreateBookRequest {

    @NotEmpty
    @Schema(description = "Значения полей: fieldName -> value", example = "{\"Title\": \"Мастер и Маргарита\", \"ISBN\": \"9785171234567\"}")
    private Map<String, String> values;

    @Schema(description = "Источник данных", example = "manual")
    private String source = "manual";

    @Schema(description = "Уверенность источника (0..1)", example = "1.0")
    private Double confidence;
}