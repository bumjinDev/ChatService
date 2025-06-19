package com.chatservice.user.service;

import com.chatservice.user.model.MemberDTO;

public interface IMemberService {

	//public Map<String, String> validLoginSuccess(String jwt);
	public void validJoin(MemberDTO memberDTO); 		// MembersRepository.checkMember() 의 결과로써 숫자 값을 그대로 반환.
	public MemberDTO searchEditMember(String editId);	// 회원 정보 조회 : 회원이 회원 정보 수정 요청 시 이를 위한 회원 검색
	public String editMember(String jwt, MemberDTO memberDTO);		// 실제 회원 수정 요청
}
