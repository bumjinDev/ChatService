package com.chatservice.marketplace.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chatservice.user.dao.MemberEntityRepository;
import com.chatservice.user.model.MembersEntity;

/**
 * 응답에 붙일 회원 닉네임을 MEMBERTBL 에서 조회한다. 회원 엔티티와 저장소는 기존 user 패키지를 그대로 쓴다.
 */
@Component
public class MemberDirectory {

	private final MemberEntityRepository memberEntityRepository;

	public MemberDirectory(MemberEntityRepository memberEntityRepository) {
		this.memberEntityRepository = memberEntityRepository;
	}

	public String nickname(String memberId) {
		return memberEntityRepository.findById(memberId).map(MembersEntity::getNickName).orElse(null);
	}

	/** 여러 회원의 닉네임을 한 번에 조회한다. 결과는 회원 ID → 닉네임이다. */
	public Map<String, String> nicknames(Collection<String> memberIds) {
		Map<String, String> result = new HashMap<>();
		if (memberIds == null || memberIds.isEmpty()) {
			return result;
		}
		List<String> ids = memberIds.stream().distinct().toList();
		for (MembersEntity member : memberEntityRepository.findMembersByIds(ids)) {
			result.put(member.getId(), member.getNickName());
		}
		return result;
	}
}
