package com.chatservice.joinroom.service;

public interface IRoomJoinService {

	public void confirmJoinRoom(int roomNumber, String userId);
	public void joinRoom(int roomNumber);
}
