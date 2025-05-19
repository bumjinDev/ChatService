package com.chatservice.web.service;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import com.chatservice.web.model.WebVO;
import com.chatservice.web.repository.IWebRepository;

@Service
public class WebService implements IWebService{
	
	IWebRepository webRepository;
	
	public WebService(IWebRepository webRepository) {
		this.webRepository = webRepository;

	}
	
	@Override
	public Map<String, Object> loadMainInfo(Authentication authentication) {
		
		/* 현재 전체 채팅 방 정보 조회 */
		List<WebVO> rooms = webRepository.webInfo();
		/* 현재 전체 채팅 사용자 수 조회 */
	    int userCount = rooms.stream()
	                         .mapToInt(WebVO::getCurrentpeople)
	                         .sum();
	    String userName = null;
	    
	    if(authentication != null) { userName = (String) authentication.getDetails(); }
	    else { userName = "none"; }
	    	    
		return Map.of(
			"userName", userName,
		    "roomCount", rooms.size(),
		    "userCount", userCount);
	}
}
