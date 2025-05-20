package com.chatservice.exportroom.service;

import org.springframework.stereotype.Service;

import com.chatservice.exportroom.dao.IExportRoomRepository;

import jakarta.transaction.Transactional;


@Service
public class ExportRoomService implements IExportRoomService{

	/**
	 * [채팅방 나가기 처리 서비스]
	 * 본 메서드는 WebSocket 세션 종료 시점 또는 명시적 퇴장 요청에 따라 호출되며, 
	 * 다음과 같은 절차로 채팅방 인원 수를 조정하고, 필요 시 방 데이터를 제거합니다.
	 * 
	 * 1. 주어진 roomNumber에 대해 현재 참여 인원 수를 1 감소시키는 DB UPDATE를 수행합니다.
	 * 2. 이후 동일 roomNumber에 대해 현재 인원 수를 SELECT로 조회합니다.
	 * 3. 인원 수가 0일 경우, 해당 방은 더 이상 유효하지 않으므로 DB에서 삭제합니다.
	 * 
	 * 주의:
	 * - 이 작업은 동시 접근 상황에서 정합성 문제가 발생할 수 있으므로, 적절한 트랜잭션 경계 또는 동시성 제어가 필요합니다.
	 * - 삭제 조건은 실시간 연결 상태와 논리적 일관성을 기준으로 명확히 정의되어야 합니다.
	 */

	IExportRoomRepository exportRoomRepository;

    public ExportRoomService(IExportRoomRepository exportRoomRepository) {
        this.exportRoomRepository = exportRoomRepository;
    }

    /**
     * [채팅방 나가기 처리 서비스]
     * 본 메서드는 WebSocket 세션 종료 시점 또는 명시적 퇴장 요청에 따라 호출되며,
     * 인원 수 감소 → 인원 확인 → 방 삭제까지 단일 트랜잭션으로 처리합니다.
     */
    @Transactional
    @Override
    public synchronized void exportRoom(int roomNumber) {
    	
        // 1. 인원 수 감소 (userCount > 0 조건 하에)
        if (exportRoomRepository.decreaseUserCount(roomNumber) == 0) {
            throw new IllegalStateException("'ROOMS' 인원수 감소(update) 실패, 해당 roomNumber가 존재하지 않거나, 이미 인원이 0입니다.");
        }
        // 2. 현재 인원 수 확인
        int currentCount = exportRoomRepository.getUserCount(roomNumber);
        // 3. 인원 수 0 → 삭제 조건 판단
        if (currentCount <= 0) {
            exportRoomRepository.deleteRoom(roomNumber);
        }
    }
}
