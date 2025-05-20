package com.chatservice.auth.exception.dto;

import org.springframework.stereotype.Component;

import com.chatservice.user.model.MemberDTO;

import java.util.List;

@Component
public class AuthenticationEntityConverter {

    /**
     * MemberDTO -> AuthenticationEntity 변환 (권한 포함)
     */
    public AuthenticationEntity toEntity(MemberDTO dto, List<String> roles) {
        if (dto == null) {
            return null;
        }

        return AuthenticationEntity.builder()
                .userid(dto.getId())
                .username(dto.getNickName())
                .password(dto.getPw()) // 패스워드는 사전에 암호화해야 함
                .roles(roles)
                .build();
    }
}
