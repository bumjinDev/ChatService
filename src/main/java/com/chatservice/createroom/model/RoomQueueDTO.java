package com.chatservice.createroom.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor      // 매개변수 없는 기본 생성자 자동 생성
@AllArgsConstructor     // 모든 필드를 인자로 받는 생성자 자동 생성
@Data
@Builder
public class RoomQueueDTO {

	private int roomNumber;
	private String roomTitle;
	private int currentPeople; 
	private int maxPeople;
}
