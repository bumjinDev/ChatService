package com.chatservice.createroom.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatservice.createroom.model.RoomQueueEntity;

public interface RoomQueueJpa extends JpaRepository<RoomQueueEntity, Integer>{
}