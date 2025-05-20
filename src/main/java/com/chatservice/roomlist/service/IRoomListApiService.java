package com.chatservice.roomlist.service;

import java.util.List;

import com.chatservice.roomlist.model.RoomDTO;

public interface IRoomListApiService {
	
	List<RoomDTO> getRoomList();
}
