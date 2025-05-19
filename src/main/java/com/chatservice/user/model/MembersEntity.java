package com.chatservice.user.model;

import java.sql.Date;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor		// 생성자 생성을 대신 하는 어노테이션, 모든 멤버 변수를 매개변수로 담는 생성자를 내부적으로 자동 등록한다.
@NoArgsConstructor		// 생성자 생성을 대신 하는 어노테이션, 매개변수가 아에 없는 생성자를 생성한다, JPA 할 때 반드시 포함해야 된다.
@Entity
@Builder
@Table(name="membertbl")
public class MembersEntity {
	
	@Id
	String id;				// 아이디
	String pw;				// 비밀번호
	String nickName;		// 회원 닉네임
	String tel;				// 회원 전화번호
	String email;			// 이메일
	Date joinDate;			// 회원 가입 날짜
}
