package com.chatservice.exportroom.dao;

public interface IExportRoomRepository {

    /**
     * 사용자 수를 1 감소시킵니다.
     * @param roomNumber 대상 방 번호
     * @return 실제로 수정된 레코드 수 (0이면 실패)
     */
    int decreaseUserCount(int roomNumber);

    /**
     * 현재 사용자 수 조회
     * @param roomNumber 대상 방 번호
     * @return 현재 인원 수
     */
    int getUserCount(int roomNumber);

    /**
     * 방 삭제
     * @param roomNumber 삭제할 방 번호
     */
    void deleteRoom(int roomNumber);
}
