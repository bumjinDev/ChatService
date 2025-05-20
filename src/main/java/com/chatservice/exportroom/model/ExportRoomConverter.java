package com.chatservice.exportroom.model;

import org.springframework.stereotype.Component;


@Component
public class ExportRoomConverter {

    public RoomDTO toDTO(ExportRoomEntity entity) {
        if (entity == null) return null;
        return new RoomDTO(
                entity.getRoomNumber(),
                entity.getRoomTitle(),
                entity.getCurrentPeople(),
                entity.getMaxPeople()
        );
    }
    
    public ExportRoomEntity toEntity(RoomDTO dto) {
        if (dto == null) return null;
        return new ExportRoomEntity(
        		dto.getRoomNumber(),
        		dto.getRoomTitle(),
        		dto.getCurrentPeople(),
        		dto.getMaxPeople()
        );
    }
}
