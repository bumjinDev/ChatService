package com.chatservice.web.service;

import java.util.List;
import java.util.Map;

import com.chatservice.joinroom.dao.ChatRoom;
import com.chatservice.websocketcore.model.ChatSessionRegistry;
import com.chatservice.web.converter.ChatRoomWebVOConverter;
import com.chatservice.web.model.WebVO;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class WebService implements IWebService {

	private final ChatSessionRegistry chatSessionRegistry;
	private final ChatRoomWebVOConverter chatRoomWebVOConverter;

	public WebService(ChatSessionRegistry chatSessionRegistry, ChatRoomWebVOConverter chatRoomWebVOConverter) {
		this.chatSessionRegistry = chatSessionRegistry;
		this.chatRoomWebVOConverter = chatRoomWebVOConverter;
	}

	@Override
	public Map<String, Object> loadMainInfo(Authentication authentication) {

		// 1. 메모리 기반 전체 방 정보 조회
		List<ChatRoom> chatRooms = List.copyOf(chatSessionRegistry.getRooms().values());

		// 2. 도메인 → View DTO 변환
		List<WebVO> rooms = chatRoomWebVOConverter.toWebVOList(chatRooms);

		// 3. 전체 사용자 수 집계
		int userCount = rooms.stream()
				.mapToInt(WebVO::getCurrentpeople)
				.sum();

		// 4. 로그인 사용자 정보 추출
		String userName = (authentication != null)
				? (String) authentication.getDetails()
				: "none";

		// 5. 결과 Map으로 출력
		return Map.of(
				"userName", userName,
				"roomCount", rooms.size(),
				"userCount", userCount
		);
	}
}
