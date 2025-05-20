package com.chatservice.user.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chatservice.user.model.MembersEntity;

/* 1. JWT 인증 위한 "UserEntityDetailService" 사용
 * 2. 회원 관리 서비스인 "MembersRepository" 사용 */
public interface MemberEntityRepository extends JpaRepository<MembersEntity, String>{
	
	Optional<MembersEntity> findById(String userId); // MembersRepository.confimMember : 회원가입 요청 시 id 값으로 검색해서 존재하는 유저인지 확인.  
	
	Optional<MembersEntity> findByNickName(String nickname);	// "UserEntityDetailService.loadUserByUsername" 에서 UserEntity 생성 위한 호출.

	@Query(value = "SELECT * FROM membertbl WHERE nickname = :nickname AND id != :id", nativeQuery = true)	// MembersRepository.editMember : 회원 수정 요청을 받아 닉네임이 중복 여부 확인.
	Optional<MembersEntity> findByNicknameAndNotIdNative(@Param("nickname") String nickname, @Param("id") String id);
	
	@Query("SELECT m FROM MembersEntity m WHERE m.id IN :userIds")
	List<MembersEntity> findMembersByIds(@Param("userIds") List<String> userIds);
}