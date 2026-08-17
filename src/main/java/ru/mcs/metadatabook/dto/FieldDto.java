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
@Schema(description = "Метаданные поля объекта")
public class FieldDto {

    @Schema(example = "Title")
    private String fieldName;

    @Schema(example = "Название")
    private String label;

    @Schema(example = "text")
    private String dataType;

    @Schema(example = "true")
    private Boolean isIndexed;

    @Schema(example = "false")
    private Boolean isRequired;
}