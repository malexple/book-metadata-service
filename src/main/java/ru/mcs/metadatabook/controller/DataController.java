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
import ru.mcs.metadatabook.entity.MtData;
import ru.mcs.metadatabook.entity.MtObject;
import ru.mcs.metadatabook.repository.MtDataRepository;
import ru.mcs.metadatabook.service.MetadataService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Tag(name = "Data", description = "API для работы с данными объектов")
public class DataController {

    private final MtDataRepository dataRepository;
    private final MetadataService metadataService;

    @Operation(
            summary = "Получить все записи объекта",
            description = "Возвращает все записи указанного объекта для организации"
    )
    @GetMapping("/{objectName}")
    public ResponseEntity<List<MtData>> getRecords(
            @Parameter(description = "ID организации", example = "1")
            @RequestParam(defaultValue = "1") Integer orgId,

            @Parameter(description = "Имя объекта", example = "Contact")
            @PathVariable String objectName) {

        MtObject object = metadataService.getObjectByName(orgId, objectName);
        List<MtData> records = dataRepository.findByOrgIdAndObjId(orgId, object.getObjId());
        return ResponseEntity.ok(records);
    }

    @Operation(
            summary = "Получить запись по GUID",
            description = "Возвращает конкретную запись по её уникальному идентификатору"
    )
    @GetMapping("/{objectName}/{guid}")
    public ResponseEntity<MtData> getRecord(
            @Parameter(description = "ID организации", example = "1")
            @RequestParam(defaultValue = "1") Integer orgId,

            @Parameter(description = "Имя объекта", example = "Contact")
            @PathVariable String objectName,

            @Parameter(description = "GUID записи", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID guid) {

        MtData record = dataRepository.findByOrgIdAndGuid(orgId, guid);
        return ResponseEntity.ok(record);
    }
}
