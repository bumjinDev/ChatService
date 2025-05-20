package com.chatservice.auth.userdetailservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.auth.exception.dto.AuthenticationEntity;
import com.chatservice.auth.repository.UserEntityRepository;
import com.chatservice.auth.userdetails.UserEntityDetails;



@Service
public class UserEntityDetailService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserEntityDetailService.class);
    
    private final UserEntityRepository userRepository;

    public UserEntityDetailService(UserEntityRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
    	logger.info("loadUserByUsername() 호출 - username: {}", username);

    	// 테이블 "userEntity" 내 회원을 조회 한다.(기존 회원 관리 테이블 과는 무관)
        AuthenticationEntity authenticationEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("사용자를 찾을 수 없음: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        logger.info("사용자 정보 로드 완료 - ID: {}, Username: {}, Roles: {}", 
        		authenticationEntity.getUserid(), authenticationEntity.getUsername(), authenticationEntity.getRoles());

        return new UserEntityDetails(authenticationEntity);
    }
}
