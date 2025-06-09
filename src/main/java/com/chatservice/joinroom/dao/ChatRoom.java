package com.chatservice.joinroom.dao;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
public class ChatRoom {

    /* 방 번호 : 사용자로부터 요청 받은 방을 서버 측에서 구별하기 위함 */
    private int roomNumber;
    /* 방 이름 : 채팅 대기방 목록 페이지에서 사용자에게 제공될 방 이름을 보이기 위함. */
    private String roomTitle;
    /* 현재 참여 인원 수 : 사실 세마포어에서 관리함.. */
    private int currentPeople;
    /* 최대 참여 가능 인원 수  : 사실 세마포어에서 관리함..*/
    private int maxPeople;
}
