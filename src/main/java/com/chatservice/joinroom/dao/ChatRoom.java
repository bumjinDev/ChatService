package com.chatservice.joinroom.dao;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @class ChatRoom
 * @brief 단일 채팅방 상태/메타정보 캡슐화 객체.
 *        - DB/메모리/레지스트리/핸들러 계층 등 모든 곳에서 단일 방의 상태 일관성·구조적 참조 책임.
 *        - 최소/최대/현재 인원, 방 번호, 방 이름, 생성 시각 등 핵심 필드만 구성(확장 필드 추가시 별도 설계).
 */
@Data
public class ChatRoom {

    /** 방 번호(식별자, NOT NULL) */
    private final int roomNumber;

    /** 방 이름(표시명) */
    private final String roomName;

    /** 최대 인원(동시 접속 한도, NOT NULL) */
    private final int maxPeople;

    /** 현재 인원(실시간, 동시성 통제는 Registry 계층 책임) */
    private int currentPeople;

    /** 방 생성 시각(로깅/TTL/모니터링 목적) */
    private final LocalDateTime createdAt;

    // ────────────── [생성자] ──────────────

    public ChatRoom(int roomNumber, String roomName, int maxPeople) {
        this.roomNumber = roomNumber;
        this.roomName = roomName;
        this.maxPeople = maxPeople;
        this.currentPeople = 0;
        this.createdAt = LocalDateTime.now();
    }

    // ────────────── [Getter/Setter] ──────────────

    public int getRoomNumber() {
        return roomNumber;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getMaxPeople() {
        return maxPeople;
    }

    /** 반드시 ChatSessionRegistry를 통해서만 호출될 것(실시간 상태 일관성 강제) */
    public int getCurrentPeople() {
        return currentPeople;
    }

    public void setCurrentPeople(int currentPeople) {
        this.currentPeople = currentPeople;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ────────────── [유틸리티/확장] ──────────────

    /** 방이 꽉 찼는지 여부(동시성/상태 통제는 Registry·Semaphore에 위임) */
    public boolean isFull() {
        return currentPeople >= maxPeople;
    }

    @Override
    public String toString() {
        return "ChatRoom{" +
                "roomNumber=" + roomNumber +
                ", roomName='" + roomName + '\'' +
                ", maxPeople=" + maxPeople +
                ", currentPeople=" + currentPeople +
                ", createdAt=" + createdAt +
                '}';
    }

    // 실무 확장시: 공개여부, 비밀번호, 태그, 방장, 커스텀 옵션 등 별도 도메인 분리
}

