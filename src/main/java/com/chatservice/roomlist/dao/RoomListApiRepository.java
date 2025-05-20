package com.chatservice.roomlist.dao;

import java.util.List;

import org.springframework.stereotype.Repository;

@Repository
public class RoomListApiRepository implements IRoomListApiRepository{

	RoomListJpa roomJpaRepository;
	
	public RoomListApiRepository(RoomListJpa roomJpaRepository) {
		this.roomJpaRepository = roomJpaRepository;
	}
	
	@Override
	public List<RoomListEntity> searchRoomList() {
		return roomJpaRepository.findAll();
	}
}
