package com.chatservice.auth.provider;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.chatservice.auth.exception.dto.AuthenticationEntity;
import com.chatservice.auth.repository.UserEntityRepository;
import com.chatservice.auth.userdetails.UserEntityDetails;
import com.chatservice.auth.userdetailservice.UserEntityDetailService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserAuthenticationProvider implements AuthenticationProvider{
	
	private static final Logger logger = LoggerFactory.getLogger(UserAuthenticationProvider.class);

	
	private final UserEntityDetailService userEntityDetailService;
	private final UserEntityRepository userEntityRepository;
	private final BCryptPasswordEncoder passwordEncoder;
	
	public UserAuthenticationProvider(
			
			UserEntityDetailService userEntityDetailService,
			UserEntityRepository userEntityRepository,
			BCryptPasswordEncoder passwordEncoder
		) {
		
		this.userEntityDetailService = userEntityDetailService;
		this.passwordEncoder = passwordEncoder;
		this.userEntityRepository = userEntityRepository;
	}
	
	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		
		logger.info("UserAuthenticationProvider.authenticate()!");
		
		String userId = authentication.getPrincipal().toString() ;		// interface Principal
		String password = authentication.getCredentials().toString();	// interface Authentication
		
		AuthenticationEntity userEntity = userEntityRepository.findByUserid(userId)
				.orElseThrow(() -> {
                    logger.warn("User ID '{}'를 찾을 수 없음", userId);
                    return new BadCredentialsException("Invalid User ID or Password");
                });
	
		String username = userEntity.getUsername();
		
		UserDetails userDetails = userEntityDetailService.loadUserByUsername(username);	// 테이블 "UserEntity" 으로부터 생성한 "AuthenticationEntity" 객체
			
		/**
		 * 🔹 인증 과정 설명 🔹
		 *
		 * 사용자가 입력한 ID와 비밀번호를 검증한다.
		 * 인증이 성공하면 `UsernamePasswordAuthenticationToken`을 생성하여 반환한다.
		 * 반환된 인증 객체는 `AuthenticationManager.authenticate()`에서 검증되고, 
		 *    Spring Security 필터 체인에서 다음 단계로 전달된다.
		 *
		 * [인증 성공 시 설정되는 값]
		 *    - `principal`  : 사용자 ID (username)
		 *    - `credentials`: 사용자의 입력한 비밀번호
		 *    - `authorities`: 사용자의 권한 목록
		 *    - `details`    : 추가적인 사용자 정보 (예: userId)
		 *
		 * [인증 객체(Token) 관련 사항]
		 *    - `AbstractAuthenticationToken.setAuthenticated(true)`: 인증 성공 여부 설정
		 *    - `isAuthenticated()`: 인증 여부 확인 (true: 인증됨, false: 인증 안됨)
		 *    - `details` 필드에 추가 정보를 저장하여 이후 JWT 생성에 활용 가능
		 *
		 *  주의: 
		 *    - `Authentication` 객체는 불변(immutable) 상태이므로, 
		 *      인증이 완료되면 새 `UsernamePasswordAuthenticationToken`을 생성하여 반환해야 한다.
		 */

		// 비밀번호 검증 후, 인증 객체 반환
		if (!passwordEncoder.matches(password, userDetails.getPassword())) {
		    logger.warn("잘못된 비밀번호 입력: 사용자 '{}'", userEntity.getUsername());
		    throw new BadCredentialsException("Invalid User ID or Password");
		}

		logger.info("인증 성공: 사용자 '{}'", userEntity.getUsername());

		/**
		 * 🔹 인증 성공 시 반환되는 객체 (`UsernamePasswordAuthenticationToken`)
		 *    - `principal`  : 사용자 ID
		 *    - `credentials`: 사용자의 입력한 비밀번호
		 *    - `authorities`: 사용자 권한 목록
		 */
		UsernamePasswordAuthenticationToken authenticationToken =
		        new UsernamePasswordAuthenticationToken(
		        		userEntity.getUsername(),
		        		password,
		        		userDetails.getAuthorities());

		// 추가적인 기타 사용자 정보를 details 필드에 저장 (JWT 생성 시 활용)
		authenticationToken.setDetails(((UserEntityDetails) userDetails).getuserId());

		return authenticationToken;
	}

	/* SpringFilterChain 내 이미 폼로그인을 막아 둠으로써  */
	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
 