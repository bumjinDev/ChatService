package com.chatservice.joinroom.dao;

import java.util.Optional;

import com.chatservice.joinroom.model.JoinRoomEntity;

public interface IRoomJoinRepository {
	
	public void incrementParticipantCount(int roomNumber);
	public JoinRoomEntity createRoomTable(JoinRoomEntity roomQueueEntity);
	Optional<JoinRoomEntity>  findRoomTable(int roomNumber);
	public Optional<RoomPeopleProjection> loadPeopleInfo(int roomNumber);
}