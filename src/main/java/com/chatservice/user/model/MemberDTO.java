package com.chatservice.user.model;

import java.sql.Date;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberDTO {

	/* "MembersAPIController" 의 @Vaild 에 따라 필터링 할 기준을 어노테이션으로 지정,
	 * 만약 잘못된 API 요청일 시 "MemberControllerExceptionHandler" 에 따라 404 코드가 반환되고 JS 에서 메시지 확인. */
	
    @NotBlank(message = "아이디는 필수 입력값입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해야 합니다.")
    private String id;

    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String pw;

    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Size(max = 20, message = "닉네임은 최대 20자까지 가능합니다.")
    private String nickName;

    @NotBlank(message = "전화번호는 필수 입력값입니다.")
    @Pattern(regexp = "^(010|011|016|017|018|019)-?\\d{3,4}-?\\d{4}$",
             message = "유효한 전화번호 형식이 아닙니다. 예: 010-1234-5678")
    private String tel;

    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "유효한 이메일 주소 형식이 아닙니다.")
    private String email;

    private Date joinDate;  // 이 필드는 서버 내부에서 설정되므로 별도 검증 불필요
}
