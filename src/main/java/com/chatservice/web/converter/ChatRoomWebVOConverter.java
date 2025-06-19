package com.chatservice.web.converter;

import com.chatservice.joinroom.dao.ChatRoom;
import com.chatservice.web.model.WebVO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ChatRoomWebVOConverter {

    /** 단일 변환 */
    public static WebVO toWebVO(ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        return new WebVO(
                chatRoom.getRoomNumber(),
                chatRoom.getRoomName(),
                chatRoom.getCurrentPeople(),
                chatRoom.getMaxPeople()
        );
    }

    /** 단일 역변환 */
    public static ChatRoom toChatRoom(WebVO webVO) {
        if (webVO == null) return null;
        ChatRoom chatRoom = new ChatRoom(
                webVO.getRomnumber(),
                webVO.getRoomname(),
                webVO.getMaxpeople()
        );
        chatRoom.setCurrentPeople(webVO.getCurrentpeople());
        return chatRoom;
    }

    /** List<ChatRoom> → List<WebVO> 변환 */
    public List<WebVO> toWebVOList(List<ChatRoom> chatRooms) {
        if (chatRooms == null || chatRooms.isEmpty()) return Collections.emptyList();
        // 내부 null 요소까지 방어
        return chatRooms.stream()
                .filter(Objects::nonNull)
                .map(ChatRoomWebVOConverter::toWebVO)
                .collect(Collectors.toList());
    }

    /** List<WebVO> → List<ChatRoom> 변환 (역방향도 필요 시) */
    public List<ChatRoom> toChatRoomList(List<WebVO> webVOs) {
        if (webVOs == null || webVOs.isEmpty()) return Collections.emptyList();
        return webVOs.stream()
                .filter(Objects::nonNull)
                .map(ChatRoomWebVOConverter::toChatRoom)
                .collect(Collectors.toList());
    }
}
