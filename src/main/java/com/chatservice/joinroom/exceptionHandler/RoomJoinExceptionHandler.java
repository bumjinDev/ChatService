package com.chatservice.joinroom.exceptionHandler;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.chatservice.joinroom.exception.RoomBadJoinFullException;

@ControllerAdvice(basePackages = "com.chatservice.joinroom.controller")
public class RoomJoinExceptionHandler {
	
	/* 방 인원 가득 찼을 때 정상적인 단순 방 입장 요청 버튼을 클릭한 게 아니라, 방이 가득차 있는 상태에서 인위적으로 입 입장 API 지점 요청 했을 때 alert 페이지 띄우는 새로운 HTML 반환하는 것. */
	@ExceptionHandler(RoomBadJoinFullException.class)
    public String handleRoomJoinFull(RoomBadJoinFullException ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        System.out.println(model.getAttribute("errorMessage"));
        return "chatservice/exception/joinError";
	}
}
