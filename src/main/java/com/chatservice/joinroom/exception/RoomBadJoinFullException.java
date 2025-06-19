package com.chatservice.joinroom.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
/* 방 인원 가득 찼을 때 정상적인 단순 방 입장 요청 버튼을 클릭한 게 아니라, 방이 가득차 있는 상태에서 인위적으로 입 입장 API 지점 요청 했을 때 alert 페이지 띄우는 새로운 HTML 반환하는 것. */
@ResponseStatus(HttpStatus.CONFLICT)
public class RoomBadJoinFullException extends RuntimeException {
	
    public RoomBadJoinFullException(String string) {
        super("방 번호 " + string + "의 최대 인원 수를 초과했습니다.");
    }
}