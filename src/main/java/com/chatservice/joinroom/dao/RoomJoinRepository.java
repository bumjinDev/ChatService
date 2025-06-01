package com.chatservice.joinroom.dao;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import com.chatservice.joinroom.dao.RoomJoinJpa;
import com.chatservice.joinroom.model.JoinRoomEntity;

/* 방 생성 대기열 테이블이 아닌, 실제 방 테이블(ROOMS) 를 가지고 조회 */
@Repository
public class RoomJoinRepository implements IRoomJoinRepository{

	RoomJoinJpa roomJoinJpa;
	
	public RoomJoinRepository(RoomJoinJpa roomJoinJpa) {
		this.roomJoinJpa = roomJoinJpa;
	}

	/* 인원수 1 증가 : update 문 */
	@Override
	public void incrementParticipantCount(int roomNumber) {roomJoinJpa.incrementCurrentPeople(roomNumber);	}
	
	@Override
	public JoinRoomEntity createRoomTable(JoinRoomEntity roomEntity) {
		return roomJoinJpa.save(roomEntity);
	}

	/* "findRoomTable" 메소드는 단순 기존 방 여부만 확인 목적 이므로, 추후 세마포어로 대체  */
	@Override
	public Optional<JoinRoomEntity> findRoomTable(int roomNumber) {
		return roomJoinJpa.findByRoomNumber(roomNumber);
	}

	/* "loadPeopleInfo" 은 필요 없음..추후 "findRoomTable" 으로 모두 사용. */
	@Override
    public Optional<RoomPeopleProjection> loadPeopleInfo(int roomNumber) {
        return roomJoinJpa.findPeopleInfoByRoomNumber(roomNumber);

    }
}