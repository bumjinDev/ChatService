package com.chatservice.user.dao;

import java.util.List;
import java.util.Optional;

import com.chatservice.auth.exception.dto.AuthenticationEntity;
import com.chatservice.user.model.MembersEntity;



public interface IMembersRepository {
	
	public void addMember(MembersEntity membersEntity, AuthenticationEntity userEntity);			// confimMember() 실행 시 이상 없으면 회원 가입 진행.
	public Optional<MembersEntity>  getMember(String userId);						// modify.jsp 페이지 제공 위한 MembersVO
	List<MembersEntity> getMembers(List<String> userIds);
	public void editMember(MembersEntity memberVO, AuthenticationEntity userEntity);					// 회원정보 수정
	Optional<MembersEntity> isNEditickNameAllowed(MembersEntity membersEntity, AuthenticationEntity userEntity);
}