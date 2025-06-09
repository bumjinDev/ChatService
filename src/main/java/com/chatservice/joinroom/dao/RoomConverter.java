package com.chatservice.joinroom.dao;
import com.chatservice.createroom.model.RoomQueueVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ChatRoom <-> RoomQueueVO 상호 변환 컨버터
 *
 * 설계 목적:
 * - RoomQueueVO는 "생성 대기열 및 임시 상태"를, ChatRoom은 "실제 메모리/DB상 방 실체"를 표현
 * - 필드 값 매핑 및 정책적 변환(입장/대기상태, 시간 등)을 구조적으로 통제
 * - RoomQueueVO→ChatRoom 변환 시 불필요 필드(대기열 상태, 생성 시간 등)는 무시, 반대 변환 시 정책적 기본값 적용
 */

@Component
public final class RoomConverter {

    private RoomConverter() {} // 유틸리티 클래스: 인스턴스화 금지

    /** RoomQueueVO -> ChatRoom 변환 */
    public ChatRoom toChatRoom(RoomQueueVO queueVO) {
        if (queueVO == null) return null;
        // RoomQueueVO의 currentPeople/maxPeople 필드는 ChatRoom과 동일하게 전달
        return new ChatRoom(
                queueVO.getRoomNumber(),
                queueVO.getRoomTitle(),
                queueVO.getCurrentPeople(),
                queueVO.getMaxPeople()
        );
    }

    /** ChatRoom -> RoomQueueVO 변환 */
    public RoomQueueVO toRoomQueueVO(ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        // 대기열 상태/생성시간 등은 정책적으로 신규 생성, joined는 기본 false
        return new RoomQueueVO(
                chatRoom.getRoomNumber(),
                chatRoom.getRoomTitle(),
                chatRoom.getMaxPeople()
        );
        // RoomQueueVO 생성자 정책: currentPeople=0, joined=false, createdTime=now
        // 실제 필요시, 생성 후 setter로 currentPeople 등 상태를 복원 가능
    }

    /** ChatRoom -> RoomQueueVO (상태 보존 변환, currentPeople 유지) */
    public RoomQueueVO toRoomQueueVOWithCurrent(ChatRoom chatRoom) {
        if (chatRoom == null) return null;
        RoomQueueVO vo = new RoomQueueVO(
                chatRoom.getRoomNumber(),
                chatRoom.getRoomTitle(),
                chatRoom.getMaxPeople()
        );
        vo.setCurrentPeople(chatRoom.getCurrentPeople());
        vo.setJoined(false);
        vo.setCreatedTime(LocalDateTime.now());
        return vo;
    }
}
