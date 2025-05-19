package com.chatservice.joinroom.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoomQueueVO {

    /* 실제 테이블 컬럼과 동일 */
    private int roomNumber;
    private String roomTitle;
    private int currentPeople;			// 현재 접속 인원수 : 생성 대기열 입장에서는 필요 없으나, 그냥 통일성으로 작성하여 실제 생성 시 그냥 별다른 고려 없이 붙여 넣기만 하면 되게
    private int maxPeople;

    /* 실제 테이블 내용이 아닌 대기열 로써 기능 위함 */
    boolean joined; 					// 실제 입장 여부
    private LocalDateTime createdTime;	// 스케줄러가 조작하기 위함.

    public RoomQueueVO(int roomNumber, String roomTitle, int maxPeople) {
        this.roomNumber = roomNumber;
        this.roomTitle = roomTitle;
        this.maxPeople = maxPeople;
        this.currentPeople = 0;
        this.joined = false;
        this.createdTime = LocalDateTime.now();
    }

    public boolean isExpired(int minutes) {
        /* 입장했으면 즉시 삭제 */
        if (joined) { return true; }
        return createdTime.plusMinutes(minutes).isBefore(LocalDateTime.now());
    }
}
