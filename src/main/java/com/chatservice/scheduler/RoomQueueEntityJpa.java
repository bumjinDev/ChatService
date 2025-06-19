package com.chatservice.scheduler;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.chatservice.createroom.model.RoomQueueEntity;

import jakarta.transaction.Transactional;

public interface RoomQueueEntityJpa extends CrudRepository<RoomQueueEntity, Integer> {

	/* Jpa.save() : 큐 테이블 로써 저장 받기 위해 사용 */
	
	/* DELETE : 큐 테이블 내 필요 없는 정보 삭제 목적
	 * 	1) 일정 시간 만큼 지정된 시간 내 접속되어 실제 테이블로써 생성된 대기열들/
	 * 	2) 일정 시간 내 접속되지 않아서 더 이상 유지할 필요 없는 대기열들.
	 */
    @Modifying
    @Transactional
    @Query("DELETE FROM RoomQueueEntity r WHERE r.roomNumber = :roomNumber")
    void deleteByRoomNumber(@Param("roomNumber") int roomNumber);
}