package ru.mcs.metadatabook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Метаданные объекта")
public class ObjectDto {

    @Schema(description = "ID объекта", example = "1")
    private Integer objId;

    @Schema(description = "ID организации", example = "1")
    private Integer orgId;

    @Schema(description = "Системное имя", example = "Contact")
    private String objName;

    @Schema(description = "Отображаемое имя", example = "Contact")
    private String label;

    @Schema(description = "Множественное имя", example = "Contacts")
    private String pluralLabel;

    @Schema(description = "Описание")
    private String description;

    @Schema(description = "Кастомный объект", example = "false")
    private Boolean isCustom;

    @Schema(description = "Активен", example = "true")
    private Boolean isActive;

    @Schema(description = "Дата создания")
    private OffsetDateTime createdAt;

    @Schema(description = "Дата изменения")
    private OffsetDateTime modifiedAt;
}
