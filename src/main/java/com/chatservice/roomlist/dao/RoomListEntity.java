package com.chatservice.roomlist.dao;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity					// JPA 사용
@Data					// setter / getter 생성
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ROOMS")
public class RoomListEntity {
	
	@Id
	@Column(name = "roomnumber")
	private int roomNumber;
	@Column(name = "roomtitle")
	private String roomTitle;
	@Column(name = "currentpeople")
	private int currentPeople; 
	@Column(name = "maxpeople")
	private int maxPeople;
}
