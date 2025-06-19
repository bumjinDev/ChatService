package com.chatservice.roomlist.model;

import com.chatservice.joinroom.dao.ChatRoom; // ChatRoom 도메인 import
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @class RoomListConverter
 * @brief ChatRoom <-> RoomDTO 변환 전용 클래스. (Entity 변환 책임은 별도 설계 권장)
 */
@Component
public class RoomListConverter {

    // ChatRoom → RoomDTO
    public RoomDTO toDto(ChatRoom chatRoom) {
        if (chatRoom == null) {
            return null;
        }
        return new RoomDTO(
                chatRoom.getRoomNumber(),
                chatRoom.getRoomName(),      // ChatRoom의 roomName을 DTO의 roomTitle로 맵핑
                chatRoom.getCurrentPeople(),
                chatRoom.getMaxPeople()
        );
    }

    // RoomDTO → ChatRoom
    public ChatRoom toChatRoom(RoomDTO dto) {
        if (dto == null) {
            return null;
        }
        // ChatRoom의 생성자에 roomNumber, roomName, maxPeople만 전달, currentPeople은 setter로 주입
        ChatRoom chatRoom = new ChatRoom(
                dto.getRoomNumber(),
                dto.getRoomTitle(),
                dto.getMaxPeople()
        );
        chatRoom.setCurrentPeople(dto.getCurrentPeople());
        return chatRoom;
    }

    // List<ChatRoom> → List<RoomDTO>
    public List<RoomDTO> toDtoList(List<ChatRoom> chatRoomList) {
        if (chatRoomList == null) {
            return null;
        }
        return chatRoomList.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // List<RoomDTO> → List<ChatRoom>
    public List<ChatRoom> toChatRoomList(List<RoomDTO> dtoList) {
        if (dtoList == null) {
            return null;
        }
        return dtoList.stream()
                .map(this::toChatRoom)
                .collect(Collectors.toList());
    }
}
