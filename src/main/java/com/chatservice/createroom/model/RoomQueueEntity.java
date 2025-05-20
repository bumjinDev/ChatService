package com.chatservice.createroom.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor      // 매개변수 없는 기본 생성자 자동 생성
@AllArgsConstructor     // 모든 필드를 인자로 받는 생성자 자동 생성
@Data
@Entity
@Table(name = "chat_room_creation_queue")
public class RoomQueueEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "roomnumber")
	private int roomNumber;
	@Column(name = "roomtitle")
	private String roomTitle;
	@Column(name = "currentpeople")
	private int currentPeople; 
	@Column(name = "maxpeople")
	private int maxPeople;
}
