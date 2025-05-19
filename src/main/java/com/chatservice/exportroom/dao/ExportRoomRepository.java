package com.chatservice.exportroom.dao;

import org.springframework.stereotype.Repository;

@Repository
public class ExportRoomRepository implements IExportRoomRepository {

    ExportRoomJpa exportRoomJpa;

    public ExportRoomRepository(ExportRoomJpa exportRoomJpa) {
        this.exportRoomJpa = exportRoomJpa;
    }

    @Override
    public int decreaseUserCount(int roomNumber) {
        return exportRoomJpa.decreaseUserCount(roomNumber);
    }

    @Override
    public int getUserCount(int roomNumber) {
        return exportRoomJpa.getUserCount(roomNumber);
    }

    @Override
    public void deleteRoom(int roomNumber) {
    	exportRoomJpa.deleteRoom(roomNumber);
    }
}
