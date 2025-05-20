package com.chatservice.createroom.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import com.chatservice.createroom.dao.IRoomQueueRepository;
import com.chatservice.createroom.memory.InMemoryRoomQueueTracker;
import com.chatservice.createroom.model.RoomCreationQueueConverter;
import com.chatservice.createroom.model.RoomQueueDTO;
import com.chatservice.createroom.model.RoomQueueVO;
import com.chatservice.createroom.model.RoomQueueEntity;

@Service
public class RoomQueueService implements IRoomQueueService{

	IRoomQueueRepository roomQueueRepository;
	RoomCreationQueueConverter roomCreationQueueConverter;
	InMemoryRoomQueueTracker inMemoryRoomQueueTracker;
	
	public RoomQueueService(
			IRoomQueueRepository roomQueueRepository,
			RoomCreationQueueConverter roomCreationQueueConverter,
			InMemoryRoomQueueTracker inMemoryRoomQueueTracker) {
		
		this.roomQueueRepository = roomQueueRepository;
		this.roomCreationQueueConverter = roomCreationQueueConverter;
		this.inMemoryRoomQueueTracker = inMemoryRoomQueueTracker;
	}

	@Transactional
	@Override
    public int creationQueueService(RoomQueueDTO roomQueueDto) {
		
        roomQueueDto.setCurrentPeople(0);
		// DB 저장
		RoomQueueEntity roomQueueEntity = roomCreationQueueConverter.toEntity(roomQueueDto);
		RoomQueueDTO roomQueueDTO = roomCreationQueueConverter.toDto(roomQueueRepository.enQueue(roomQueueEntity));
        RoomQueueVO roomQueueVO = roomCreationQueueConverter.toVO(roomQueueDTO);
       
        /* VO 생성 후 메모리 저장 */
        inMemoryRoomQueueTracker.addRoom(roomQueueVO);

        return roomQueueDTO.getRoomNumber();
	}
}
