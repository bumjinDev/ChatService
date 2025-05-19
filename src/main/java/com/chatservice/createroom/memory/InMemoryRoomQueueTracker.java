package com.chatservice.createroom.memory;

import com.chatservice.createroom.model.RoomQueueEntity;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.joinroom.service.RoomJoinService;
import com.chatservice.scheduler.RoomQueueEntityJpa;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRoomQueueTracker {

    private final Map<Integer, RoomQueueVO> roomQueueMap = new ConcurrentHashMap<>();

    private final RoomQueueEntityJpa roomQueueEntityJpa;

    private static final Logger logger = LoggerFactory.getLogger(InMemoryRoomQueueTracker.class);


    public InMemoryRoomQueueTracker(RoomQueueEntityJpa roomQueueEntityJpa) {
        this.roomQueueEntityJpa = roomQueueEntityJpa;
    }

    @PostConstruct
    public void initializeTrackerFromDatabase() {
        List<RoomQueueEntity> dbRooms = (List<RoomQueueEntity>) roomQueueEntityJpa.findAll();
        for (RoomQueueEntity entity : dbRooms) {
            RoomQueueVO vo = new RoomQueueVO(
                    entity.getRoomNumber(),
                    entity.getRoomTitle(),
                    entity.getMaxPeople()
            );
            vo.setCurrentPeople(entity.getCurrentPeople());
            vo.setCreatedTime(LocalDateTime.now()); // 복원 시점 기준 시간 부여
            vo.setJoined(false); // 복원된 상태는 미입장으로 간주

            roomQueueMap.put(vo.getRoomNumber(), vo);
        }
        System.out.println("[초기화 완료] DB → RoomQueueMap 로드됨: " + dbRooms.size() + "건");
    }

    // 기존 메서드 그대로 유지

    public void addRoom(RoomQueueVO room) {
        roomQueueMap.put(room.getRoomNumber(), room);
    }

    public RoomQueueVO getRoom(int roomNumber) {

        logger.info("새로운 방 생성 대기열 검색, 벙 번호 : {}", roomNumber);
        return roomQueueMap.get(roomNumber);
    }

    public void markAsJoined(int roomNumber) {
        RoomQueueVO room = roomQueueMap.get(roomNumber);
        if (room != null) room.setJoined(true);
    }

    public List<RoomQueueVO> removeExpiredRooms(int minutes) {
        List<RoomQueueVO> expiredRooms = roomQueueMap
                .values().stream()
                .filter(room -> room.isExpired(minutes))
                .toList();

        for (RoomQueueVO room : expiredRooms) {
            roomQueueMap.remove(room.getRoomNumber());
        }

        return expiredRooms;
    }

    public Map<Integer, RoomQueueVO> getRoomQueueMap() {
        return roomQueueMap;
    }
}
