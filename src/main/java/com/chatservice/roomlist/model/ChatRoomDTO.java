package com.chatservice.roomlist.model;

import com.chatservice.joinroom.dao.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data					// setter / getter 생성
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {
	
	private int roomNumber;
	private String roomTitle;
	private int currentPeople; 
	private int maxPeople;

	public static ChatRoomDTO from(ChatRoom room) {
		if (room == null) return null;
		return new ChatRoomDTO(
				room.getRoomNumber(),
				room.getRoomTitle(),
				room.getCurrentPeople(),
				room.getMaxPeople()
		);
	}
}