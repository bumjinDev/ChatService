package com.chatservice.auth.userdetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.chatservice.auth.exception.dto.AuthenticationEntity;


public class UserEntityDetails implements UserDetails {

	private static final long serialVersionUID = 1L;
	
	AuthenticationEntity authenticationEntity;
	
	public UserEntityDetails (AuthenticationEntity userEntity) {
		this.authenticationEntity = userEntity;
	}
	
	public String getuserId() {
		
		return (String) this.authenticationEntity.getUserid();
	}
	
	/* Interface - GrantedAuthority:
	 *  권한을 문자열로 반환하는 단일 메소드 "String getAuthority();"를 가진 인터페이스.
	 *  여기서는 Collection에 담아 여러 권한을 반환할 수 있도록 처리한다.
	 */

	/* Interface - Collection<? extends GrantedAuthority>:
	 *  자바의 Collection 인터페이스는 int size(), boolean isEmpty()와 같은 메소드를 제공하며,
	 *  List와 Set 같은 컬렉션 클래스가 이를 구현하여 사용한다. ('Iterable<E>' 을 상속받아 Iterator<E> iterator() 도 같이 포함한다).
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
	    
		// 역할을 GrantedAuthority 리스트로 변환
	    List<GrantedAuthority> authorities = new ArrayList<>();
	    
	    for (String role : authenticationEntity.getRoles()) {  // userEntity의 roles는 String의 리스트입니다.
	        authorities.add(new SimpleGrantedAuthority(role));
	    }
	    return authorities;
	}


	@Override
	public String getPassword() {
		// TODO Auto-generated method stub
		return authenticationEntity.getPassword();
	}

	@Override
	public String getUsername() {
		// TODO Auto-generated method stub
		return authenticationEntity.getUsername();
	}

	/* 자격 증명 만료 여부를 검증하고 결과를 반환하는 것(JWT 에서는 active 토큰에 대한 검증을 수정하면 될 듯)*/
	@Override
	public boolean isCredentialsNonExpired(){
		
		return false;
	}
	
	@Override
	public boolean isEnabled() {
		
		return true;
	}
}
