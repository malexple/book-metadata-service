package ru.mcs.metadatabook.mapper;

import org.mapstruct.Mapper;
import ru.mcs.metadatabook.dto.ObjectDto;
import ru.mcs.metadatabook.entity.MtObject;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ObjectMapper {

    ObjectDto toDto(MtObject entity);

    List<ObjectDto> toDtoList(List<MtObject> entities);
}
