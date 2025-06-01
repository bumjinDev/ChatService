package com.chatservice;

import com.chatservice.roomlist.dao.RoomListEntity;
import com.chatservice.roomlist.dao.RoomListJpa;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@EnableScheduling
@SpringBootApplication
public class ChatServiceApplication {

	@Autowired
	RoomListJpa roomListJpa;

	public static void main(String[] args) {
		SpringApplication.run(ChatServiceApplication.class, args);
	}

	@PostConstruct
	public void check() {
		List<RoomListEntity> rooms = roomListJpa.findAll();
		System.out.println("Room size: " + rooms.size());
	}

}
