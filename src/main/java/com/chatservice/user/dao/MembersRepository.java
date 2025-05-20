package com.chatservice.user.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.chatservice.auth.exception.dto.AuthenticationEntity;
import com.chatservice.auth.repository.UserEntityRepository;
import com.chatservice.user.model.MembersEntity;


/**
 * MembersRepository
 *
 * 회원 관리 도메인의 데이터 접근 계층으로,
 * 회원 정보(MembersEntity) 및 인증 정보(AuthenticationEntity)를 함께 처리한다.
 * 
 * 주요 역할:
 * - 회원 가입 시 회원 테이블과 인증 테이블에 동시에 데이터 저장
 * - 회원 정보 조회 및 수정 기능 제공
 * - 닉네임 중복 여부 확인 로직 포함
 * 
 * 구성 요소:
 * - memberEntityRepository : 회원 관리 테이블 전용 JPA Repository
 * - userEntityRepository   : Spring Security 기반 인증 테이블 전용 Repository
 */
@Repository
public class MembersRepository implements IMembersRepository {

    private final MemberEntityRepository memberEntityRepository;
    private final UserEntityRepository userEntityRepository;

    public MembersRepository(MemberEntityRepository memberEntityRepository,
                             UserEntityRepository userEntityRepository) {
        this.memberEntityRepository = memberEntityRepository;
        this.userEntityRepository = userEntityRepository;
    }

    /**
     * 회원 가입 처리
     *
     * 회원 테이블과 인증 테이블에 동시에 정보를 저장한다.
     * Spring Security 기반 인증 흐름을 위해 사용자 권한 정보도 함께 저장한다.
     *
     * @param membersEntity       회원 관리 테이블에 저장할 회원 정보 엔티티
     * @param authenticationEntity 인증 테이블에 저장할 인증 정보 엔티티
     */
    @Override
    public void addMember(MembersEntity membersEntity, AuthenticationEntity authenticationEntity) {
        memberEntityRepository.save(membersEntity);
        userEntityRepository.save(authenticationEntity);
    }

    /**
     * 회원 단건 조회
     *
     * 주어진 사용자 ID에 해당하는 회원 정보를 조회한다.
     *
     * @param userId 조회 대상 사용자 ID
     * @return 회원 정보 엔티티 (Optional 반환, 존재하지 않을 경우 empty)
     */
    @Override
    public Optional<MembersEntity> getMember(String userId) {
        return memberEntityRepository.findById(userId);
    }

    /**
     * 다중 회원 닉네임 조회
     *
     * 게시글, 댓글 작성자 정보를 보여주기 위한 사용자 ID 목록에 대한 닉네임 조회 메서드.
     *
     * @param userIds 사용자 ID 목록
     * @return 닉네임 목록 (userIds와 매핑 순서를 보장해야 함)
     */
    @Override
    public List<MembersEntity> getMembers(List<String> userIds) {
        return memberEntityRepository.findMembersByIds(userIds);
    }

    /**
     * 닉네임 중복 여부 검사 (본인 제외)
     *
     * 회원 수정 요청 시, 본인의 ID를 제외한 상태에서 동일한 닉네임이 존재하는지 검사한다.
     * 존재할 경우 닉네임 중복이므로 수정이 제한된다.
     *
     * @param membersEntity       수정 요청 회원 정보
     * @param userEntity          인증 정보 (사용되지 않음, 구조 상 일관성 확보용)
     * @return 닉네임 중복된 회원 정보 (존재하지 않으면 empty)
     */
    @Override
    public Optional<MembersEntity> isNEditickNameAllowed(MembersEntity membersEntity, AuthenticationEntity userEntity) {
        return memberEntityRepository.findByNicknameAndNotIdNative(
            membersEntity.getNickName(),
            membersEntity.getId()
        );
    }

    /**
     * 회원 정보 수정 처리
     *
     * 회원 테이블과 인증 테이블 정보를 함께 업데이트한다.
     * Spring Security 인증 구조와 회원 관리 정보를 동시에 갱신해야 하므로, 두 테이블 모두 저장한다.
     *
     * @param membersEntity  갱신할 회원 정보
     * @param userEntity     갱신할 인증 정보
     */
    @Override
    public void editMember(MembersEntity membersEntity, AuthenticationEntity userEntity) {
        memberEntityRepository.save(membersEntity);
        userEntityRepository.save(userEntity);
    }
}
