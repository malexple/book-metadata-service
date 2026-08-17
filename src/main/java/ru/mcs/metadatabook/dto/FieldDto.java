package ru.mcs.metadatabook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Метаданные поля")
public class FieldDto {

    @Schema(description = "ID поля", example = "1")
    private Integer fieldId;

    @Schema(description = "Системное имя поля", example = "FirstName")
    private String fieldName;

    @Schema(description = "Отображаемое имя", example = "First Name")
    private String label;

    @Schema(description = "Тип данных", example = "text")
    private String dataType;

    @Schema(description = "Номер слота в flex-колонках (0-49)", example = "0")
    private Integer fieldNum;

    @Schema(description = "Индексируемое поле", example = "true")
    private Boolean isIndexed;

    @Schema(description = "Уникальное поле", example = "false")
    private Boolean isUnique;

    @Schema(description = "Обязательное поле", example = "false")
    private Boolean isRequired;
}
