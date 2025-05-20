package com.chatservice.roomlist.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomListJpa extends JpaRepository<RoomListEntity, Integer>{
	
	List<RoomListEntity> findAll();
}
