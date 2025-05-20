package com.chatservice.joinroom.model;

import org.springframework.stereotype.Component;
import com.chatservice.createroom.model.RoomQueueVO;


@Component
public class JoinRoomConverter {

    public RoomDTO toDTO(JoinRoomEntity entity) {
        if (entity == null) return null;
        return new RoomDTO(
                entity.getRoomNumber(),
                entity.getRoomTitle(),
                entity.getCurrentPeople(),
                entity.getMaxPeople()
        );
    }
    
    /* 여기서 VO 를 create 도메인에서 가져오는 이유는 생성 대기방 리스트를 create 도메인에서 관리하며 여기서 싱글톤트로 띄우기 때문 */
    public RoomDTO toDTO(RoomQueueVO VO) {
        if (VO == null) return null;
        return new RoomDTO(
        		VO.getRoomNumber(),
        		VO.getRoomTitle(),
        		VO.getCurrentPeople(),
        		VO.getMaxPeople()
        );
    }

    public JoinRoomEntity toEntity(RoomDTO dto) {
        if (dto == null) return null;
        return new JoinRoomEntity(
        		dto.getRoomNumber(),
        		dto.getRoomTitle(),
        		dto.getCurrentPeople(),
        		dto.getMaxPeople()
        );
    }
}
