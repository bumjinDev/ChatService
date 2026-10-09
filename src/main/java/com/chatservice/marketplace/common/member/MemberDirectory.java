package com.chatservice.marketplace.common.member;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatservice.user.dao.MemberEntityRepository;
import com.chatservice.user.model.MembersEntity;

/**
 * 응답에 붙일 회원 닉네임을 기존 MEMBERTBL 에서 읽는다. 기존 회원 저장소를 수정하지 않고 그대로 사용한다.
 */
@Component
public class MemberDirectory {

    private final MemberEntityRepository memberEntityRepository;

    public MemberDirectory(MemberEntityRepository memberEntityRepository) {
        this.memberEntityRepository = memberEntityRepository;
    }

    public Map<String, String> nicknames(Collection<String> memberIds) {
        Map<String, String> result = new HashMap<>();
        if (memberIds.isEmpty()) {
            return result;
        }
        List<MembersEntity> members = memberEntityRepository.findMembersByIds(List.copyOf(memberIds));
        for (MembersEntity member : members) {
            result.put(member.getId(), member.getNickName());
        }
        return result;
    }

    public String nickname(String memberId) {
        return memberEntityRepository.findById(memberId).map(MembersEntity::getNickName).orElse(null);
    }
}
