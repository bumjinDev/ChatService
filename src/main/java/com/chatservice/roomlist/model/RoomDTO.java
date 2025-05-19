package com.chatservice.roomlist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data					// setter / getter 생성
@NoArgsConstructor
@AllArgsConstructor
public class RoomDTO {
	
	private int roomNumber;
	private String roomTitle;
	private int currentPeople; 
	private int maxPeople;
}