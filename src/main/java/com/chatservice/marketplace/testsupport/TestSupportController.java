package com.chatservice.marketplace.testsupport;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 겹침 재현 테스트가 멈춘 요청을 확인하고 재개하는 API.
 *
 * 테스트 설정({@code marketplace.test-support.enabled})이 켜져 있을 때만 등록된다. {@code /test-support/**}는 어느 보안 필터 체인에도
 * 속하지 않아 인증 없이 호출된다. 외부에서 접근할 수 있는 서버이므로 테스트하는 동안에만 설정을 켠다.
 */
// REST API 컨트롤러로 등록한다. 메서드의 반환값은 JSON으로 응답한다.
@RestController
// 이 클래스의 API를 /test-support/pauses 아래에 둔다. 서버의 실제 주소는 context-path를 붙인 /ChatService/test-support/pauses다.
@RequestMapping("/test-support/pauses")
// 테스트 설정이 true일 때만 이 컨트롤러를 등록한다. 설정이 꺼져 있으면 이 주소의 요청을 처리할 컨트롤러가 없다.
@ConditionalOnProperty(name = "marketplace.test-support.enabled", havingValue = "true")
public class TestSupportController {

    // 멈춰 있는 요청 목록과 재개 기능을 가진 객체.
    private final TestPause testPause;

    // TestPause 빈을 생성자로 주입받는다.
    public TestSupportController(TestPause testPause) {
        this.testPause = testPause;
    }

    /**
     * 지금 멈춰 있는 요청의 이름 목록을 조회한다.
     *
     * @return 예) {@code ["A","B"]}
     */
    // GET /test-support/pauses 요청을 처리한다.
    @GetMapping
    public Set<String> waiting() {
            // 지금 멈춰 있는 요청 이름 목록을 그대로 돌려준다. JMeter는 이 목록에 A와 B가 모두 있는지 확인한다.
            return testPause.waitingNames();
    }

    /**
     * 멈춰 있는 요청을 재개한다.
     *
     * @param name 요청 이름
     * @return 재개했으면 200, 그 이름으로 멈춰 있는 요청이 없으면 409
     */
    // POST /test-support/pauses/{name}/resume 요청을 처리한다. {name} 자리에 A 또는 B를 넣는다.
    @PostMapping("/{name}/resume")
    public ResponseEntity<Void> resume(@PathVariable("name") String name) {
        // 주소의 이름으로 멈춰 있는 요청을 재개한다.
        // 재개했으면 200, 그 이름으로 멈춰 있는 요청이 없으면 409를 본문 없이 돌려준다.
        return testPause.resume(name)
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
