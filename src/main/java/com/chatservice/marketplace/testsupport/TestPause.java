package com.chatservice.marketplace.testsupport;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 겹침 재현 테스트용 요청 일시 중지.
 *
 * 서비스 코드의 중지 위치에서 {@link #reach}를 호출한다. 다음 조건을 모두 만족하는 요청만 그 위치에서 멈추고,
 * 나머지 요청과 HTTP 요청 밖의 호출(스케줄러 등)은 바로 진행한다.
 * - 테스트 설정 {@code marketplace.test-support.enabled}가 켜져 있다.
 * - 요청 헤더 {@link #NAME_HEADER}에 요청 이름(예: A, B)이 있다.
 * - 요청 헤더 {@link #PAUSE_AT_HEADER}의 값이 호출한 위치의 {@link PausePoint} 이름과 같다.
 *
 * 멈춘 요청은 트랜잭션과 DB 연결을 잡은 채로 기다린다. 그래서 {@code marketplace.test-support.pause-timeout}(기본 30초) 안에
 * 재개되지 않으면 예외를 던져 그 요청을 실패로 끝낸다.
 */
// 스프링 빈으로 등록한다. OrderCancellationService와 TestSupportController가 생성자로 주입받는다.
@Component
public class TestPause {

    // 요청 이름(A, B)을 담는 요청 헤더의 이름. JMeter가 요청 A와 요청 B에 붙인다.
    public static final String NAME_HEADER = "X-Test-Request-Name";
    // 멈출 위치(ORDER_READ 또는 WALLET_READ)를 담는 요청 헤더의 이름. JMeter가 요청 A와 요청 B에 붙인다.
    public static final String PAUSE_AT_HEADER = "X-Test-Pause-At";

    // 멈춤과 재개를 서버 로그에 남기는 로거. 테스트 결과를 확인할 때 이 로그로 순서를 본다.
    private static final Logger logger = LoggerFactory.getLogger(TestPause.class);

    // 테스트 설정 marketplace.test-support.enabled의 값. false이면 reach()는 아무것도 하지 않는다.
    private final boolean enabled;
    // 멈춘 요청이 재개 신호를 기다리는 최대 시간. 설정 marketplace.test-support.pause-timeout의 값이며 기본은 30초다.
    private final Duration timeout;

    /** 지금 멈춰 있는 요청. 요청 이름 → 재개 신호. */
    // 멈춘 요청의 스레드가 값을 넣고(put), 재개 API를 처리하는 다른 스레드가 값을 꺼낸다(remove).
    // 여러 스레드가 동시에 쓰므로 ConcurrentHashMap을 쓴다.
    private final Map<String, CountDownLatch> waiting = new ConcurrentHashMap<>();

    // 설정값을 주입받는다. 설정이 없으면 enabled는 false, timeout은 30초(PT30S)가 된다.
    public TestPause(@Value("${marketplace.test-support.enabled:false}") boolean enabled,
                     @Value("${marketplace.test-support.pause-timeout:PT30S}") Duration timeout) {
        this.enabled = enabled;
        this.timeout = timeout;
    }

    /**
     * 현재 요청이 이 위치에서 멈추도록 지정되어 있으면 재개될 때까지 기다린다. 그렇지 않으면 바로 반환한다.
     *
     * @param point 호출한 코드의 중지 위치
     * @throws IllegalStateException 대기 시간 안에 재개되지 않았다
     */
    public void reach(PausePoint point) {
        // 1. 테스트 설정이 꺼져 있으면 멈추지 않고 바로 진행한다. 운영 상태에서는 항상 여기서 반환된다.
        if (!enabled) {
            return;
        }
        // 2. 지금 처리 중인 HTTP 요청을 가져온다. 스케줄러의 자동 취소처럼 HTTP 요청 밖에서 호출되면 null이다.
        HttpServletRequest request = currentRequest();
        // HTTP 요청이 아니면 멈추지 않는다.
        if (request == null) {
            return;
        }
        // 3. 요청 이름 헤더를 읽는다. 테스트 헤더가 없는 일반 요청이면 null이다.
        String name = request.getHeader(NAME_HEADER);
        // 요청 이름이 없거나, 멈출 위치 헤더의 값이 지금 호출한 위치와 다르면 멈추지 않는다.
        if (name == null || !point.name().equals(request.getHeader(PAUSE_AT_HEADER))) {
            return;
        }

        // 4. 이 요청을 깨울 재개 신호를 만든다. countDown()이 한 번 호출되면 await()의 대기가 풀린다.
        CountDownLatch resumeSignal = new CountDownLatch(1);
        // 5. "이 이름의 요청이 멈춰 있다"고 목록에 올린다. 상태 조회 API가 이 목록을 보여 준다.
        waiting.put(name, resumeSignal);
        logger.info("[테스트 일시 중지] 대기 시작 name={}, point={}", name, point);
        try {
            // 6. 재개 신호가 올 때까지 이 요청의 스레드가 기다린다. 기다리는 동안 트랜잭션과 DB 연결은 열린 채로 있다.
            //    timeout 안에 신호가 오지 않으면 await()가 false를 돌려준다.
            if (!resumeSignal.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                // 대기 시간을 넘기면 예외를 던진다. 취소 메서드의 트랜잭션은 롤백되고 요청은 오류로 끝난다.
                throw new IllegalStateException("테스트 일시 중지 대기 시간을 넘겼습니다. name=" + name + ", point=" + point);
            }
        } catch (InterruptedException e) {
            // 기다리던 스레드가 중단되면(서버 종료 등) 중단 표시를 되살리고 요청을 오류로 끝낸다.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("테스트 일시 중지 대기 중 중단되었습니다. name=" + name, e);
        } finally {
            // 7. 재개되었든 대기 시간을 넘겼든 목록에서 지운다.
            //    같은 이름으로 나중에 멈춘 다른 요청의 신호를 지우지 않도록, 키와 함께 값(이 요청의 신호)까지 같을 때만 지운다.
            waiting.remove(name, resumeSignal);
        }
        logger.info("[테스트 일시 중지] 재개 name={}, point={}", name, point);
        // 8. 여기서 반환되면 취소 처리가 reach() 다음 줄부터 이어진다.
    }

    /**
     * 이름이 같은 요청이 멈춰 있으면 재개한다.
     *
     * @param name 요청 이름
     * @return 재개했으면 {@code true}, 그 이름으로 멈춰 있는 요청이 없으면 {@code false}
     */
    public boolean resume(String name) {
        // 1. 목록에서 이 이름의 재개 신호를 꺼내면서 지운다. 같은 요청을 두 번 재개하지 않게 된다.
        CountDownLatch resumeSignal = waiting.remove(name);
        // 그 이름으로 멈춰 있는 요청이 없으면 false를 돌려준다. 컨트롤러는 이때 409로 응답한다.
        if (resumeSignal == null) {
            return false;
        }
        // 2. 재개 신호를 보낸다. reach()의 await()에서 기다리던 요청 스레드가 깨어나 처리를 이어 간다.
        resumeSignal.countDown();
        return true;
    }

    /** 지금 멈춰 있는 요청의 이름을 이름순으로 돌려준다. */
    public Set<String> waitingNames() {
        // 목록의 키(요청 이름)를 새 집합에 복사해 이름순으로 정렬한다. 원래 목록은 바꾸지 않는다.
        return new TreeSet<>(waiting.keySet());
    }

    private static HttpServletRequest currentRequest() {
        // Spring MVC는 요청을 처리하는 스레드에 현재 요청 정보를 묶어 둔다. 그 정보가 HTTP 요청이면 요청 객체를 꺼낸다.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        // 묶인 요청 정보가 없으면(스케줄러 스레드 등) null을 돌려준다.
        return null;
    }
}
