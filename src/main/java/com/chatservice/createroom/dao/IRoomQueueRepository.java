package com.chatservice.createroom.dao;

import com.chatservice.createroom.model.RoomQueueEntity;

public interface IRoomQueueRepository {

	public RoomQueueEntity enQueue(RoomQueueEntity roomQueueEntity);
}
