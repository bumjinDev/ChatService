package com.chatservice.createroom.model;

import org.springframework.stereotype.Component;

@Component
public class RoomCreationQueueConverter {

    /**
     * RoomCreationQueueEntity 객체를 RoomCreationQueueDto 객체로 변환합니다.
     * 데이터베이스에서 읽어온 대기열 Entity를 DTO로 변환하여 서비스/컨트롤러 간 전달하거나 응답 데이터로 사용합니다.
     *
     * @param entity 변환할 RoomCreationQueueEntity 객체
     * @return 변환된 RoomCreationQueueDto 객체
     */
    public RoomQueueDTO toDto(RoomQueueEntity entity) {
        if (entity == null) {
            return null;
        }
        // Lombok @AllArgsConstructor 사용 또는 빌더 패턴, 혹은 기본 생성자 + Setter 사용
        return new RoomQueueDTO(
                entity.getRoomNumber(), // Entity의 roomNumber 사용
                entity.getRoomTitle(),
                entity.getCurrentPeople(),
                entity.getMaxPeople()
        );
    }

    public RoomQueueVO toVO(RoomQueueDTO DTO) {
        if (DTO == null) return null;
        return new RoomQueueVO(
        		DTO.getRoomNumber(),
        		DTO.getRoomTitle(),
        		DTO.getMaxPeople()
        );
    }
    
    /**
     * RoomCreationQueueDto 객체를 RoomCreationQueueEntity 객체로 변환합니다.
     * 클라이언트로부터 받은 DTO 데이터를 데이터베이스에 저장하기 위해 Entity로 변환할 때 사용합니다.
     *
     * <p>주의: RoomCreationQueueEntity의 roomNumber 필드는 {@code @GeneratedValue}로 자동 생성되므로,
     * 새로운 엔티티를 저장할 경우 DTO의 roomNumber 값을 Entity에 설정하지 않아야 합니다.
     * 만약 DTO가 이미 존재하는 대기열 엔티티의 정보를 담고 있다면 (예: 업데이트),
     * 그때는 roomNumber를 설정할 수도 있습니다.
     *
     * @param dto 변환할 RoomCreationQueueDto 객체
     * @return 변환된 RoomCreationQueueEntity 객체
     */
    public RoomQueueEntity toEntity(RoomQueueDTO dto) {
        if (dto == null) {
            return null;
        }
        // 새로운 Entity 생성 시 @GeneratedValue 필드(roomNumber)는 설정하지 않아야 합니다.
        // @NoArgsConstructor와 Setter를 사용하는 것이 안전합니다.
        RoomQueueEntity entity = new RoomQueueEntity();
        // entity.setRoomNumber(dto.getRoomNumber()); // <-- 새로운 엔티티 생성 시 이 줄은 제외해야 합니다.
        entity.setRoomTitle(dto.getRoomTitle());
        entity.setCurrentPeople(dto.getCurrentPeople());
        entity.setMaxPeople(dto.getMaxPeople());

        return entity;
    }
}