package com.chatservice.joinroom.converter;

import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.joinroom.dao.ChatRoom;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RoomConverter {

    public ChatRoom toChatRoom(RoomQueueVO vo) {
        if (vo == null)
            throw new IllegalArgumentException("RoomQueueVO is null");

        return new ChatRoom(
                vo.getRoomNumber(),
                vo.getRoomTitle(), // 필드명만 다름
                vo.getMaxPeople()
        );
        // currentPeople, createdAt은 생성자에서 자동 세팅
    }

    public RoomQueueVO toRoomQueueVO(ChatRoom chatRoom) {
        if (chatRoom == null)
            throw new IllegalArgumentException("ChatRoom is null");

        RoomQueueVO vo = new RoomQueueVO(
                chatRoom.getRoomNumber(),
                chatRoom.getRoomName(),
                chatRoom.getMaxPeople()
        );
        vo.setCurrentPeople(chatRoom.getCurrentPeople());
        vo.setJoined(true);
        vo.setCreatedTime(chatRoom.getCreatedAt());
        return vo;
    }
}
