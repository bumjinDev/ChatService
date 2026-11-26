package com.chatservice.marketplace.product.lock;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 상품마다 잠금 객체를 하나씩 보관한다.
 *
 * 가격 제안 수락과 결제는 같은 상품의 요청을 한 건씩 실행하려고, 이 클래스가 돌려준 잠금 객체를 지정해 {@code synchronized} 블록을 연다.
 * {@code synchronized} 는 지정한 객체가 같은 요청끼리만 서로 기다리게 한다. 요청마다 만들어지는 상품 ID(Long) 객체는 숫자가 같아도
 * 서로 다른 객체일 수 있으므로, 상품 ID 의 숫자가 같으면 언제나 같은 객체를 돌려주는 이 맵을 둔다.
 *
 * 맵에 넣은 잠금 객체는 지우지 않는다. 잠금을 기다리는 요청이 있는데 그 상품의 잠금 객체를 지우면, 다음 요청이 새 객체를 받아
 * 기다리던 요청과 서로 기다리지 않게 된다. 서버가 실행되는 동안 수락이나 결제가 들어온 상품 수만큼 잠금 객체가 남고, 서버를 다시 시작하면 비워진다.
 */
@Component
public class ProductLockRegistry {

    // 상품 ID → 그 상품의 잠금 객체. 여러 요청 스레드가 동시에 찾고 넣으므로 ConcurrentHashMap 을 쓴다
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    /**
     * 상품의 잠금 객체를 돌려준다. 같은 상품 ID 로 호출하면 언제나 같은 객체를 돌려준다.
     *
     * 상품 ID 가 맵에 없으면 새 객체를 만들어 보관한 뒤 돌려준다. 「없으면 만들어 넣는다」를 한 동작으로 실행하므로,
     * 같은 상품의 첫 요청 두 건이 동시에 호출해도 잠금 객체는 하나만 만들어지고 두 요청이 같은 객체를 받는다.
     *
     * @param productId 상품 ID
     * @return 그 상품의 잠금 객체. {@code synchronized} 블록에 지정해 쓴다
     */
    public Object lockOf(Long productId) {
        return locks.computeIfAbsent(productId, id -> new Object());
    }
}
