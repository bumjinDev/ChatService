package com.chatservice.exportroom.dao;

import com.chatservice.exportroom.model.ExportRoomEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportRoomJpa extends JpaRepository<ExportRoomEntity, Integer> {

    // 인원수 감소: 음수 방지 조건 포함
    @Modifying
    @Transactional
    @Query("UPDATE ExportRoomEntity r SET r.currentPeople = r.currentPeople - 1 WHERE r.roomNumber = :roomNumber AND r.currentPeople > 0")
    int decreaseUserCount(@Param("roomNumber") int roomNumber);

    // 현재 인원수 조회
    @Query("SELECT r.currentPeople FROM ExportRoomEntity r WHERE r.roomNumber = :roomNumber")
    int getUserCount(@Param("roomNumber") int roomNumber);

    // 방 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM ExportRoomEntity r WHERE r.roomNumber = :roomNumber")
    void deleteRoom(@Param("roomNumber") int roomNumber);
}
