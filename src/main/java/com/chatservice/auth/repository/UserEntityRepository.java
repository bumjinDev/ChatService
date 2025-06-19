package com.chatservice.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chatservice.auth.exception.dto.AuthenticationEntity;

public interface UserEntityRepository extends JpaRepository<AuthenticationEntity, String>{
	
	Optional<AuthenticationEntity> findByUsername(String nickName);
	Optional<AuthenticationEntity> findByUserid(String userid);
}