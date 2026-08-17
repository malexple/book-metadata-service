package ru.mcs.metadatabook.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.mcs.metadatabook.dto.FieldDto;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.service.MetadataService;

import java.util.List;

/**
 * Служебный API для UI-конструктора: показать список объектов/полей,
 * чтобы пользователь галочками выбрал нужные и получил пример вызова
 * /api/v1/books?fields=... (см. BookApiController).
 */
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Tag(name = "Metadata", description = "Метаданные объектов и полей для UI-конструктора")
public class MetadataController {

    private final MetadataService metadataService;

    @Operation(summary = "Список объектов (Book, Author, Magazine...)")
    @GetMapping("/objects")
    public ResponseEntity<List<MtObject>> getObjects(
            @Parameter(example = "1") @RequestParam(defaultValue = "1") Integer orgId) {
        return ResponseEntity.ok(metadataService.getObjectsByOrg(orgId));
    }

    @Operation(summary = "Поля объекта — источник для чекбоксов в UI-конструкторе")
    @GetMapping("/objects/{objectName}/fields")
    public ResponseEntity<List<FieldDto>> getFields(
            @Parameter(example = "1") @RequestParam(defaultValue = "1") Integer orgId,
            @Parameter(example = "Book") @PathVariable String objectName) {

        MtObject object = metadataService.getObjectByName(orgId, objectName);
        List<FieldDto> dtos = metadataService.getFieldsByObject(orgId, object.getObjId()).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private FieldDto toDto(MtField f) {
        return FieldDto.builder()
                .fieldName(f.getFieldName())
                .label(f.getLabel())
                .dataType(f.getDataType())
                .isIndexed(f.getIsIndexed())
                .isRequired(f.getIsRequired())
                .build();
    }
}