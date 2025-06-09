package com.chatservice.roomlist.service;

import java.util.List;

import com.chatservice.joinroom.dao.ChatRoom;
import com.chatservice.roomlist.model.ChatRoomDTO;

public interface IRoomListApiService {
	
	List<ChatRoomDTO> getRoomList();
}
