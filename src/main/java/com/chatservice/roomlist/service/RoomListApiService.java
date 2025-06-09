package com.chatservice.roomlist.service;

import java.util.List;
import java.util.stream.Collectors;

import com.chatservice.joinroom.dao.ChatRoom;
import com.chatservice.joinroom.dao.ChatRoomMemoryRegistry;
import org.springframework.stereotype.Service;

import com.chatservice.roomlist.model.ChatRoomDTO;


@Service
public class RoomListApiService implements IRoomListApiService{

	ChatRoomMemoryRegistry chatRoomMemoryRegistry;
	
	public RoomListApiService(ChatRoomMemoryRegistry chatRoomMemoryRegistry) {
		this.chatRoomMemoryRegistry = chatRoomMemoryRegistry;
	}
	
	@Override
	public List<ChatRoomDTO> getRoomList() {

		return roomConverter();
	}

	// roomMap의 값들을 ChatRoomDTO로 변환하여 리스트로 반환
	private List<ChatRoomDTO> roomConverter() {
		return chatRoomMemoryRegistry.getRooms().values()
				.stream()
				.map(ChatRoomDTO::from) // ChatRoom → ChatRoomDTO로 변환
				.collect(Collectors.toList());
	}
}
