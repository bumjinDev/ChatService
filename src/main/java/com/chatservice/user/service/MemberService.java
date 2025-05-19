package com.chatservice.user.service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.security.Key;
import java.sql.Date;
import java.time.Duration;

import javax.crypto.spec.SecretKeySpec;

import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.chatservice.auth.exception.dto.AuthenticationEntityConverter;
import com.chatservice.auth.filter.util.JWTUtil;
import com.chatservice.auth.repository.UserEntityRepository;
import com.chatservice.redis.handler.RedisHandler;
import com.chatservice.user.dao.IMembersRepository;
import com.chatservice.user.dao.MemberEntityRepository;
import com.chatservice.user.exception.JwtKeyNotFoundException;
import com.chatservice.user.exception.MemberNotFoundException;
import com.chatservice.user.exception.NicknameAlreadyExistsException;
import com.chatservice.user.exception.UserIdAlreadyExistsException;
import com.chatservice.user.model.MemberConverter;
import com.chatservice.user.model.MemberDTO;
import com.chatservice.user.model.MembersEntity;

import org.slf4j.Logger;

/**
 * MemberService
 *
 * 회원 관련 핵심 비즈니스 로직을 담당하는 서비스 클래스.
 * 회원 가입, 정보 수정, JWT 갱신 등 주요 기능을 제공하며,
 * 인증 정보와 회원 정보를 동시에 관리한다.
 *
 * 기술 구성:
 * - JWT 기반 인증 구조 (Redis 연동)
 * - 회원 테이블 + Spring Security 인증 테이블 동시 갱신
 * - 사용자 정의 예외를 통한 예외 흐름 제어
 */
@Service
public class MemberService implements IMemberService {

    private static final Logger logger = LoggerFactory.getLogger(MemberService.class);

    private final IMembersRepository membersRepository;
    private final MemberEntityRepository memberEntityRepository;
    private final UserEntityRepository userEntityRepository;
    private final RedisHandler redisHandler;
    private final JWTUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MemberConverter memberConverter;
    private final AuthenticationEntityConverter authenticationEntityConverter;

    public final int USER_ID_DUPLICATE = 1;
    public final int NICKNAME_DUPLICATE = 2;

    public MemberService(
    		
        IMembersRepository membersRepository,
        MemberEntityRepository memberEntityRepository,
        UserEntityRepository userEntityRepository,
        RedisHandler redisHandler,
        JWTUtil jwtUtil,
        BCryptPasswordEncoder passwordEncoder,
        MemberConverter memberConverter,
        AuthenticationEntityConverter authenticationEntityConverter) {

        this.membersRepository = membersRepository;
        this.memberEntityRepository = memberEntityRepository;
        this.userEntityRepository = userEntityRepository;
        this.redisHandler = redisHandler;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.memberConverter = memberConverter;
        this.authenticationEntityConverter = authenticationEntityConverter;
    }


    /**
     * 회원 가입 요청 처리
     * ID 및 닉네임 중복 검증, 비밀번호 암호화 후
     * 회원 테이블과 인증 테이블에 사용자 정보 등록.
     *
     * @param memberDTO 클라이언트로부터 전달받은 가입 요청 데이터
     * @throws UserIdAlreadyExistsException 아이디 중복
     * @throws NicknameAlreadyExistsException 닉네임 중복
     */
    @Override
    public void validJoin(MemberDTO memberDTO) {
        logger.info("MemberService.validJoin()");

        if (memberEntityRepository.findById(memberDTO.getId()).isPresent()) {
            throw new UserIdAlreadyExistsException("이미 사용 중인 아이디입니다.");
        }
        if (memberEntityRepository.findByNickName(memberDTO.getNickName()).isPresent()) {
            throw new NicknameAlreadyExistsException("이미 사용 중인 닉네임입니다.");
        }

        memberDTO.setPw(passwordEncoder.encode(memberDTO.getPw()));
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));

        membersRepository.addMember(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, List.of("ROLE_USER"))
        );
    }

    /**
     * 회원 정보 수정 페이지 요청 시, 사용자의 정보를 조회하여 DTO로 반환
     *
     * @param editId 조회 대상 사용자 ID
     * @return 사용자 정보 DTO
     * @throws MemberNotFoundException 존재하지 않는 사용자 ID
     * 
     * JPA.update
     */
    @Override
    public MemberDTO searchEditMember(String editId) {
    	
        return memberConverter.toDTO(membersRepository.getMember(editId)
                						.orElseThrow(() -> new MemberNotFoundException("현재 존재하지 않는 사용자 ID 입니다")));
    }

    /**
     * 회원 정보 수정 처리
     * 비밀번호 암호화, 닉네임 중복 검사, 테이블 갱신 및 JWT 토큰 재발급 포함.
     *
     * @param currentToken 기존 JWT 토큰
     * @param memberDTO 수정 요청된 회원 정보
     * @return 새롭게 갱신된 JWT 토큰
     * @throws MemberNotFoundException 사용자 ID 존재하지 않을 경우
     * @throws NicknameAlreadyExistsException 닉네임 중복 시
     */
    @Override
    public String editMember(String currentToken, MemberDTO memberDTO) {
    	
        logger.info("MemberService.editMember()");

        List<String> roles = userEntityRepository.findById(memberDTO.getId())
            .orElseThrow(() -> new MemberNotFoundException("현재 존재하지 않는 사용자 ID 입니다"))
            .getRoles();

        memberDTO.setPw(passwordEncoder.encode(memberDTO.getPw()));
        memberDTO.setJoinDate(new Date(System.currentTimeMillis()));

        Optional<MembersEntity> membersEntity = membersRepository.isNEditickNameAllowed(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, roles)  
        );

        if(membersEntity.isPresent())
        	throw new NicknameAlreadyExistsException("닉네임이 중복되어 수정할 수 없습니다.");

        membersRepository.editMember(
            memberConverter.toEntity(memberDTO),
            authenticationEntityConverter.toEntity(memberDTO, roles)
        );

        Key userKey = getKey(currentToken);
        String newToken = editToken(currentToken, userKey, "username", memberDTO.getNickName());
        updateJwtToken(currentToken, newToken, userKey);

        return newToken;
    }

    // ======== 내부 헬퍼 메서드 ========

    /**
     * 기존 JWT 토큰의 특정 클레임을 수정하여 새로운 토큰 반환
     *
     * @param currentToken 기존 JWT 문자열
     * @param userKey JWT 서명 키
     * @param claimName 수정할 클레임 이름
     * @param newUsername 새 닉네임
     * @return 수정된 JWT 토큰 문자열
     */
    private String editToken(String currentToken, Key userKey, String claimName, String newUsername) {
        return jwtUtil.modifyClaim(currentToken, userKey, claimName, newUsername);
    }

    /**
     * Redis 내 JWT 토큰 상태를 갱신한다.
     * - 새로운 토큰과 키를 등록 (TTL: 1시간)
     * - 기존 토큰과 키는 제거
     *
     * @param currentToken 기존 JWT
     * @param newToken 갱신된 JWT
     * @param userKey 서명 키
     */
    private void updateJwtToken(String currentToken, String newToken, Key userKey) {
        logger.info("MemberService.updateJwtToken()");

        redisHandler.getValueOperations().set(
            newToken,
            jwtUtil.encodeKeyToBase64(userKey),
            Duration.ofHours(1)
        );

        redisHandler.getValueOperations().getAndDelete(currentToken);
    }

    /**
     * Redis에서 현재 토큰에 대응하는 서명 키를 복원한다.
     *
     * @param currentToken JWT 문자열
     * @return 복원된 서명 키 객체
     * @throws JwtKeyNotFoundException Redis에 키 정보가 존재하지 않는 경우
     */
    private Key getKey(String currentToken) {
        logger.info("MemberService.getKey() : {}", currentToken);

        String encodedKey = (String) redisHandler.getValueOperations().get(currentToken);
        if (encodedKey == null) {
            throw new JwtKeyNotFoundException("JWT 토큰에 대한 키 정보를 Redis에서 찾을 수 없습니다.");
        }

        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedBytes, "HmacSHA256");
    }
}