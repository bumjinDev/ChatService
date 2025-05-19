package com.chatservice.joinroom.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.joinroom.model.JoinRoomEntity;

public interface RoomJoinJpa extends JpaRepository<JoinRoomEntity, Integer>{
	
	@Modifying
    @Transactional
    @Query("UPDATE JoinRoomEntity r SET r.currentPeople = r.currentPeople + 1 WHERE r.roomNumber = :roomNumber")
    void incrementCurrentPeople(@Param("roomNumber") int roomNumber);
	
	Optional<JoinRoomEntity> findByRoomNumber(int roomNumber);
	
	/* 현재 지정된 방(방 생성 대기열 목록 아님) 내 입장 가능한 지 검사하기 위한 것 */
	@Query("SELECT r.currentPeople AS currentPeople, r.maxPeople AS maxPeople FROM JoinRoomEntity r WHERE r.roomNumber = :roomNumber")
	Optional<RoomPeopleProjection> findPeopleInfoByRoomNumber(@Param("roomNumber") int roomNumber);
}