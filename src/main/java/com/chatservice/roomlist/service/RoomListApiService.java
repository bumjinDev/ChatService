package com.chatservice.roomlist.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.chatservice.roomlist.dao.IRoomListApiRepository;
import com.chatservice.roomlist.model.RoomDTO;

import com.chatservice.roomlist.model.RoomListConverter;

@Service
public class RoomListApiService implements IRoomListApiService{

	IRoomListApiRepository roomListApiRepository;
	RoomListConverter roomConverter;
	
	public RoomListApiService(
					IRoomListApiRepository roomListApiRepository,
					RoomListConverter roomConverter) {
		
		this.roomListApiRepository = roomListApiRepository;
		this.roomConverter = roomConverter;
	}
	
	@Override
	public List<RoomDTO> getRoomList() {
		return roomConverter.toDtoList(roomListApiRepository.searchRoomList());
	}
}
