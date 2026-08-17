package ru.mcs.metadatabook.dto;

import java.util.UUID;

public record DataRowDto(
        Integer orgId,
        UUID guid,
        Integer objId,
        String name,
        String value0,
        String value1,
        String value2,
        String value3,
        String value4
) {}
