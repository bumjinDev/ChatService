package com.chatservice.createroom.dao;

import org.springframework.stereotype.Repository;

import com.chatservice.createroom.model.RoomQueueEntity;

@Repository
public class RoomQueueRepository implements IRoomQueueRepository {

	RoomQueueJpa roomQueueJpa;
	
	public RoomQueueRepository(RoomQueueJpa roomQueueJpa) {
		this.roomQueueJpa = roomQueueJpa;
	}
	
	@Override
	public RoomQueueEntity enQueue(RoomQueueEntity roomQueueEntity) {
		return  roomQueueJpa.save(roomQueueEntity);
	}
}
