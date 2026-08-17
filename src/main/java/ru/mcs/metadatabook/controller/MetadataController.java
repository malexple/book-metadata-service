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
import ru.mcs.metadatabook.dto.ObjectDto;
import ru.mcs.metadatabook.entity.MtField;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.mapper.ObjectMapper;
import ru.mcs.metadatabook.service.MetadataService;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
@Tag(name = "Metadata", description = "API для работы с метаданными объектов и полей")
public class MetadataController {

    private final MetadataService metadataService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Получить все объекты организации")
    @GetMapping("/objects")
    public ResponseEntity<List<ObjectDto>> getObjects(
            @Parameter(description = "ID организации", example = "1")
            @RequestParam(defaultValue = "1") Integer orgId) {

        List<MtObject> objects = metadataService.getObjectsByOrg(orgId);
        return ResponseEntity.ok(objectMapper.toDtoList(objects));
    }

    @Operation(summary = "Получить объект по имени")
    @GetMapping("/objects/{objectName}")
    public ResponseEntity<ObjectDto> getObject(
            @Parameter(description = "ID организации", example = "1")
            @RequestParam(defaultValue = "1") Integer orgId,

            @Parameter(description = "Имя объекта", example = "Contact")
            @PathVariable String objectName) {

        MtObject object = metadataService.getObjectByName(orgId, objectName);
        return ResponseEntity.ok(objectMapper.toDto(object));
    }

    @Operation(summary = "Получить поля объекта")
    @GetMapping("/objects/{objectName}/fields")
    public ResponseEntity<List<MtField>> getFields(
            @Parameter(description = "ID организации", example = "1")
            @RequestParam(defaultValue = "1") Integer orgId,

            @Parameter(description = "Имя объекта", example = "Contact")
            @PathVariable String objectName) {

        MtObject object = metadataService.getObjectByName(orgId, objectName);
        List<MtField> fields = metadataService.getFieldsByObject(orgId, object.getObjId());
        return ResponseEntity.ok(fields);
    }
}
