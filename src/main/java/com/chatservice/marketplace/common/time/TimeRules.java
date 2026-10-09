package com.chatservice.marketplace.common.time;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/**
 * 업무 시간 규칙(BR-006)을 한 곳에서 계산한다.
 *
 * - 저장과 비교는 모두 Instant(UTC)로 하고, 영업일과 자정 경계만 KST(Asia/Seoul)로 계산한다.
 * - 48시간은 실제 경과 시간이다. 수동 요청은 "현재 < 기한"일 때만 허용하고,
 *   자동 처리는 "현재 >= 기한"일 때 대상으로 삼는다. 따라서 정확히 48시간이 되는 순간부터 수동 요청은 거절된다.
 */
@Component
public class TimeRules {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    public static final Duration INSPECTION_PERIOD = Duration.ofHours(48);
    public static final Duration REFUND_RESPONSE_PERIOD = Duration.ofHours(48);
    private static final int SHIPMENT_BUSINESS_DAYS = 5;

    private final Clock clock;

    public TimeRules(Clock clock) {
        this.clock = clock;
    }

    /**
     * 현재 시각을 마이크로초 단위로 절삭해 반환한다.
     *
     * DB 시각 컬럼이 TIMESTAMP(6)(마이크로초 정밀도)이므로, 저장한 뒤 다시 조회한 값과 메모리의 값이 같도록 같은 정밀도로 맞춘다.
     * 시각은 생성자로 주입받은 {@link Clock}에서 구한다.
     *
     * @return 마이크로초 단위로 절삭한 현재 시각
     */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * 발송 기한이 지나는 시점.
     * 구매 확정일(KST) 다음 영업일을 1일째로 세어 5번째 영업일이 끝나는 순간, 즉 그 다음 날 00:00 KST 이다.
     * 영업일은 월~금이며 공휴일은 따로 빼지 않는다.
     * 예) 월요일 확정 → 화·수·목·금·월 → 화요일 00:00 KST, 금요일·토요일 확정 → 다음 주 토요일 00:00 KST.
     */
    public Instant shipDeadlineAt(Instant confirmedAt) {
        LocalDate day = confirmedAt.atZone(BUSINESS_ZONE).toLocalDate();
        int counted = 0;
        while (counted < SHIPMENT_BUSINESS_DAYS) {
            day = day.plusDays(1);
            if (isBusinessDay(day)) {
                counted++;
            }
        }
        return day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    /**
     * 상품 확인 기한을 계산한다.
     *
     * 배송 완료 시각에 {@link #INSPECTION_PERIOD}(48시간)를 더한 값이다. 48시간은 실제 경과 시간이며 영업일과 관계없다.
     *
     * @param deliveredAt 배송 완료 시각
     * @return 상품 확인 기한
     */
    public Instant inspectionDeadlineAt(Instant deliveredAt) {
        return deliveredAt.plus(INSPECTION_PERIOD);
    }

    public Instant refundResponseDeadlineAt(Instant requestedAt) {
        return requestedAt.plus(REFUND_RESPONSE_PERIOD);
    }

    /**
     * 현재 시각이 기한보다 이른지 확인한다.
     *
     * 기한과 정확히 같은 시각은 기한이 지난 것으로 본다.
     * - 사용자 요청(발송 등록, 구매 확정, 환불 요청, 환불 응답)은 이 메서드가 {@code true}일 때만 허용한다.
     * - 자동 처리(미발송 자동 취소, 확인 기간 만료 자동 완료, 무응답 자동 환불)는 이 메서드가 {@code false}인 주문을 처리한다.
     *
     * @param now      현재 시각
     * @param deadline 기한
     * @return {@code now}가 {@code deadline}보다 이르면 {@code true}
     */
    public static boolean isBeforeDeadline(Instant now, Instant deadline) {
        return now.isBefore(deadline);
    }

    private static boolean isBusinessDay(LocalDate day) {
        DayOfWeek dayOfWeek = day.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
