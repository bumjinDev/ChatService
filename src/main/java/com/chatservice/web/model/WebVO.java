package com.chatservice.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor      // 매개변수 없는 기본 생성자 자동 생성
@AllArgsConstructor     // 모든 필드를 인자로 받는 생성자 자동 생성
@Data
public class WebVO {
	
	int romnumber;
	String roomname;
	int currentpeople;
	int maxpeople;
}
