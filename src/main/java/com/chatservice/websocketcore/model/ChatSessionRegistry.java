package com.chatservice.websocketcore.model;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * [WebSocket 세션 관리 레지스트리]
 * 본 클래스는 WebSocket 기반 채팅 시스템에서 방 번호 및 사용자 ID 기준으로
 * 세션 객체를 이중 구조로 관리하기 위한 전역 상태 객체이며,
 * 동시 접속 사용자 추적 및 브로드캐스트 시 대상 세션 목록 조회를 담당한다.
 */
@Component
public class ChatSessionRegistry {

    /**
     * [roomList] : 브로드 캐스티 목적 - 방 번호 → 해당 방의 WebSocket 세션 집합
     * 
     * 목적:
     * 1. 방별 전체 사용자에게 메시지 브로드캐스트를 하기 위한 기준 세션 목록
     * 2. WebSocket 연결 시점(afterConnectionEstablished)에서 인원 수 증가
     * 3. WebSocket 종료 시점(afterConnectionClosed)에서 인원 수 감소
     */
    public Map<String, HashSet<WebSocketSession>> roomList = new HashMap<>();
    /**
     * [roomUserSessions] : 방 번호 → (userId → WebSocket 세션) 맵
     * 
     * 목적:
     * 1. 동일 사용자가 브라우저 탭을 여러 개 열어 중복 접속할 경우 선제적 감지 및 차단
     * 2. 동일 사용자가 새로 고침 시 중복 세션 접근 방지
     * 2. 비정상 종료된 세션(브라우저 강제 종료, 재부팅 등) 이후 빠른 중복 감지
     * 3. beforeHandshake 시 userName 기반 중복 여부 확인 → 이미 존재 시 이전 세션 강제 종료
     * 
     * Map<RoomNumber, Map<userId, WebSocketSession>>
     */
    public Map<String, Map<String, WebSocketSession>> roomUserSessions = new HashMap<>();

    /**
     * @method containsUser
     * @brief 특정 방에 특정 사용자가 이미 연결되어 있는지 여부 확인
     *
     * @param roomId 방 번호 (String 형태)
     * @param userId 사용자 ID
     * @return true: 이미 연결된 사용자 / false: 연결되지 않음
     */
    public boolean containsUser(String roomId, String userId) {
        Map<String, WebSocketSession> userSessions = roomUserSessions.get(roomId);
        return userSessions != null && userSessions.containsKey(userId);
    }
}
