package com.chatservice.roomlist.model;

// @Component 어노테이션 추가
import org.springframework.stereotype.Component; // 또는 @Service 등

import com.chatservice.roomlist.dao.RoomListEntity;

import java.util.List;
import java.util.stream.Collectors;

@Component // Spring Bean으로 등록
public class RoomListConverter { 

    // RoomEntity를 RoomDTO로 변환
    public RoomDTO toDto(RoomListEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RoomDTO(
                entity.getRoomNumber(),
                entity.getRoomTitle(),
                entity.getCurrentPeople(),
                entity.getMaxPeople()
        );
    }

    // RoomDTO를 RoomEntity로 변환
    public RoomListEntity toEntity(RoomDTO dto) {
         if (dto == null) {
            return null;
        }
        return new RoomListEntity(
                dto.getRoomNumber(),
                dto.getRoomTitle(),
                dto.getCurrentPeople(),
                dto.getMaxPeople()
        );
    }

    // RoomEntity 리스트를 RoomDTO 리스트로 변환
    // 내부에서 this.toDto 호출
    public List<RoomDTO> toDtoList(List<RoomListEntity> entityList) {
        if (entityList == null) {
            return null;
        }
        return entityList.stream()
                         .map(this::toDto) // this.toDto 또는 toDto로 호출
                         .collect(Collectors.toList());
    }

    // RoomDTO 리스트를 RoomEntity 리스트로 변환
     // 내부에서 this.toEntity 호출
    public List<RoomListEntity> toEntityList(List<RoomDTO> dtoList) {
        if (dtoList == null) {
            return null;
        }
        return dtoList.stream()
                      .map(this::toEntity) // this.toEntity 또는 toEntity로 호출
                      .collect(Collectors.toList());
    }
}