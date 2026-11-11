package com.chatservice.marketplace.testsupport;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chatservice.marketplace.common.error.BusinessException;

/**
 * 거래 정합성 테스트의 공통 로그.
 *
 * 한 사건을 한 줄로 남긴다. 형식은 다음과 같다.
 * {@code 시각 - 테스트명 - 기능명 - 파일명 - 메소드명 | 실행ID=... 요청ID=... 단계=... | 테스트별 확인 항목...}
 *
 * 테스트명, 실행 ID, 요청 ID 는 {@link TradeTestLogFilter}가 요청 헤더에서 읽어 요청을 처리하는 스레드에 묶어 두고(MDC),
 * 요청이 끝나면 지운다. 이 값이 없는 요청(테스트 헤더가 없는 요청, 테스트 설정이 꺼진 서버의 요청, 스케줄러 호출)에서는
 * {@link #event}와 {@link #registerTransactionEnd}가 아무것도 기록하지 않는다. 업무 처리의 값과 순서는 바꾸지 않는다.
 */
public final class TradeTestLog {

    /** 테스트명을 담는 요청 헤더. 한글을 쓸 수 있도록 URL 인코딩(UTF-8)한 값을 받는다. 예) 예약–예약 */
    public static final String TEST_NAME_HEADER = "X-Test-Log-Name";
    /** 실행 ID 를 담는 요청 헤더. 같은 테스트를 여러 번 실행할 때 실행을 구분한다. */
    public static final String RUN_ID_HEADER = "X-Test-Log-Run-Id";
    /** 요청 ID 를 담는 요청 헤더. 요청 하나의 수신부터 응답까지를 한 값으로 잇는다. 가격 제안을 등록할 때 본문에 넣는 requestId 와는 다른 값이다. */
    public static final String REQUEST_ID_HEADER = "X-Test-Log-Request-Id";

    private static final String MDC_TEST_NAME = "tradeTestLog.testName";
    private static final String MDC_RUN_ID = "tradeTestLog.runId";
    private static final String MDC_REQUEST_ID = "tradeTestLog.requestId";
    private static final String MDC_ERROR_CODE = "tradeTestLog.errorCode";
    private static final String MDC_FAILURE = "tradeTestLog.failure";
    private static final String MDC_CALC_REMAINING = "tradeTestLog.calcRemaining";
    private static final String MDC_CALC_RESERVED_SUM = "tradeTestLog.calcReservedSum";
    private static final String MDC_CALC_AT = "tradeTestLog.calcAt";

    private static final ZoneId LOG_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");
    private static final int MAX_HEADER_LENGTH = 64;
    private static final String NONE = "-";

    private TradeTestLog() {
    }

    /** 지금 스레드가 테스트 요청을 처리하는 중이면 {@code true}. */
    public static boolean isActive() {
        return MDC.get(MDC_REQUEST_ID) != null;
    }

    /**
     * 요청 헤더의 값을 지금 스레드에 묶는다. {@link TradeTestLogFilter}가 요청을 받을 때 호출한다.
     *
     * @param encodedTestName URL 인코딩된 테스트명. 없으면 {@code -}로 기록한다.
     * @param runId           실행 ID
     * @param requestId       요청 ID
     */
    static void begin(String encodedTestName, String runId, String requestId) {
        MDC.put(MDC_TEST_NAME, clean(decode(encodedTestName)));
        MDC.put(MDC_RUN_ID, clean(runId));
        MDC.put(MDC_REQUEST_ID, clean(requestId));
    }

    /** 지금 스레드에 묶은 값을 모두 지운다. 다음 요청이 같은 스레드를 다시 쓸 때 값이 섞이지 않게 한다. */
    static void end() {
        MDC.remove(MDC_TEST_NAME);
        MDC.remove(MDC_RUN_ID);
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_ERROR_CODE);
        MDC.remove(MDC_FAILURE);
        MDC.remove(MDC_CALC_REMAINING);
        MDC.remove(MDC_CALC_RESERVED_SUM);
        MDC.remove(MDC_CALC_AT);
    }

    /** 업무 오류 코드를 적어 둔다. 요청 응답 로그가 이 값을 쓴다. */
    public static void recordErrorCode(String errorCode) {
        if (isActive()) {
            MDC.put(MDC_ERROR_CODE, clean(errorCode));
        }
    }

    /** 요청 응답 로그에 쓸 업무 오류 코드. 없으면 {@code -}. */
    static String errorCode() {
        String code = MDC.get(MDC_ERROR_CODE);
        return code == null ? NONE : code;
    }

    /**
     * 트랜잭션 안에서 던져진 예외를 실패 사유로 적어 둔다. 트랜잭션 종료 로그가 이 값을 쓴다.
     * 업무 오류이면 오류 코드를, 그 밖의 예외이면 예외 클래스 이름을 적는다.
     */
    public static void recordFailure(RuntimeException e) {
        if (!isActive()) {
            return;
        }
        String reason = (e instanceof BusinessException business)
                ? business.getErrorCode().name()
                : e.getClass().getSimpleName();
        MDC.put(MDC_FAILURE, clean(reason));
    }

    /** 수량 계산에 쓴 남은 수량, 유효 예약 합계, 판단 기준 시각을 적어 둔다. 테스트 요청이 아니면 아무것도 하지 않는다. */
    public static void rememberQuantityCalculation(long remaining, long reservedSum, Instant at) {
        if (!isActive()) {
            return;
        }
        MDC.put(MDC_CALC_REMAINING, String.valueOf(remaining));
        MDC.put(MDC_CALC_RESERVED_SUM, String.valueOf(reservedSum));
        MDC.put(MDC_CALC_AT, String.valueOf(at));
    }

    public static String calculatedRemaining()   { return valueOrNone(MDC_CALC_REMAINING); }
    public static String calculatedReservedSum() { return valueOrNone(MDC_CALC_RESERVED_SUM); }
    public static String calculatedAt()          { return valueOrNone(MDC_CALC_AT); }

    private static String valueOrNone(String key) {
        String value = MDC.get(key);
        return value == null ? NONE : value;
    }

    /**
     * 사건 하나를 공통 형식으로 남긴다. 테스트 요청이 아니면 아무것도 하지 않는다.
     *
     * @param logger  호출한 클래스의 로거
     * @param feature 기능명
     * @param file    로그를 남기는 파일명
     * @param method  로그를 남기는 메소드명
     * @param stage   단계
     * @param items   테스트별 확인 항목. 이름과 값을 번갈아 넣는다. 예) "상품ID", 101, "요청수량", 5
     */
    public static void event(Logger logger, String feature, String file, String method, String stage, Object... items) {
        if (!isActive()) {
            return;
        }
        logger.info(line(feature, file, method, stage, items));
    }

    /**
     * 지금 진행 중인 트랜잭션이 실제로 끝난 뒤에 결과를 남기도록 등록한다.
     *
     * - DB 커밋이 끝난 직후: 단계 {@code 커밋완료}
     * - DB 롤백이 끝난 뒤: 단계 {@code 롤백완료}와 실패 사유
     * - 커밋·롤백 결과를 알 수 없을 때: 단계 {@code 확정여부미확인}
     *
     * 메소드가 반환되는 시점이 아니라 트랜잭션 관리자가 커밋·롤백을 마친 뒤에 호출되는 콜백에서 기록한다.
     * 테스트 요청이 아니거나 진행 중인 트랜잭션이 없으면 아무것도 등록하지 않는다.
     */
    public static void registerTransactionEnd(Logger logger, String feature, String file, String method, Object... items) {
        if (!isActive() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                event(logger, feature, file, method, "커밋완료", items);
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                String stage = (status == STATUS_ROLLED_BACK) ? "롤백완료" : "확정여부미확인";
                String failure = MDC.get(MDC_FAILURE);
                Object[] withFailure = new Object[items.length + 2];
                System.arraycopy(items, 0, withFailure, 0, items.length);
                withFailure[items.length] = "실패사유";
                withFailure[items.length + 1] = (failure == null) ? "미상(커밋 처리 중 오류 또는 사유를 적지 못한 예외)" : failure;
                event(logger, feature, file, method, stage, withFailure);
            }
        });
    }

    private static String line(String feature, String file, String method, String stage, Object... items) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(OffsetDateTime.now(LOG_ZONE).format(LOG_TIME))
                .append(" - ").append(MDC.get(MDC_TEST_NAME))
                .append(" - ").append(feature)
                .append(" - ").append(file)
                .append(" - ").append(method)
                .append(" | 실행ID=").append(MDC.get(MDC_RUN_ID))
                .append(" 요청ID=").append(MDC.get(MDC_REQUEST_ID))
                .append(" 단계=").append(stage)
                .append(" |");
        for (int i = 0; i + 1 < items.length; i += 2) {
            sb.append(' ').append(items[i]).append('=').append(items[i + 1]);
        }
        return sb.toString();
    }

    private static String decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 인코딩이 잘못된 값은 받은 그대로 쓴다.
            return value;
        }
    }

    /** 로그 한 줄의 형식이 깨지지 않도록 줄바꿈과 제어 문자, 구분 문자(|)를 빼고 길이를 제한한다. 값이 없으면 {@code -}. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}|]", "").trim();
        if (cleaned.isEmpty()) {
            return NONE;
        }
        return cleaned.length() > MAX_HEADER_LENGTH ? cleaned.substring(0, MAX_HEADER_LENGTH) : cleaned;
    }
}
