package com.chatservice.roomlist.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.chatservice.joinroom.dao.ChatRoom;
import com.chatservice.websocketcore.model.ChatSessionRegistry;
import com.chatservice.roomlist.model.RoomDTO;
import com.chatservice.roomlist.model.RoomListConverter;

@Service
public class RoomListApiService implements IRoomListApiService {

	private final RoomListConverter roomConverter;
	private final ChatSessionRegistry chatSessionRegistry;

	public RoomListApiService(
			RoomListConverter roomConverter,
			ChatSessionRegistry chatSessionRegistry) {
		this.roomConverter = roomConverter;
		this.chatSessionRegistry = chatSessionRegistry;
	}

	@Override
	public List<RoomDTO> getRoomList() {
		// 1. ChatRoom 목록 조회 (실제 데이터 소스는 ChatSessionRegistry, DB, 캐시 등)
		List<ChatRoom> chatRoomList = new ArrayList<>(chatSessionRegistry.getRooms().values());
		// 2. 변환기로 DTO 리스트 변환 후 반환
		return roomConverter.toDtoList(chatRoomList);
	}
}
