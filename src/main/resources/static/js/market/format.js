/*
 * 금액·시각 표시와 상태 이름.
 * API 시각은 ISO-8601 UTC 문자열이며(설계 5.1), 화면에는 업무 기준 시간대인 한국 시간으로 표시한다.
 */
const numberFormat = new Intl.NumberFormat("ko-KR");
const KST = "Asia/Seoul";
const dateTimeFormat = new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST, year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23"
});
const dayFormat = new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST, year: "numeric", month: "long", day: "numeric", weekday: "short"
});
const timeFormat = new Intl.DateTimeFormat("ko-KR", { timeZone: KST, hour: "2-digit", minute: "2-digit", hourCycle: "h23" });

export const MAX_AMOUNT = 999999999999999;

export function formatNumber(value) {
    return numberFormat.format(value);
}

/* number 와 BigInt 를 모두 받는다. */
export function formatWon(value) {
    return numberFormat.format(value) + "원";
}

/* 저장 범위 판단은 서버가 하며, 화면의 합계는 큰 수에서도 정확하도록 BigInt 로 계산한다. */
export function multiplyAmount(unitPrice, quantity) {
    return BigInt(unitPrice) * BigInt(quantity);
}

function parts(formatter, date) {
    const result = {};
    formatter.formatToParts(date).forEach((part) => { result[part.type] = part.value; });
    return result;
}

export function formatDateTime(iso) {
    if (!iso) {
        return "-";
    }
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) {
        return "-";
    }
    const p = parts(dateTimeFormat, date);
    return `${p.year}.${p.month}.${p.day} ${p.hour}:${p.minute}`;
}

export function formatDay(iso) {
    return dayFormat.format(new Date(iso));
}

export function dayKey(iso) {
    const p = parts(dateTimeFormat, new Date(iso));
    return `${p.year}-${p.month}-${p.day}`;
}

export function formatTime(iso) {
    return timeFormat.format(new Date(iso));
}

/* 기한까지 남은 시간. 브라우저 시계 기준의 참고 값이며 기한 판단은 서버가 한다. */
export function timeLeft(iso) {
    if (!iso) {
        return null;
    }
    const diff = new Date(iso).getTime() - Date.now();
    if (diff <= 0) {
        return "기한 지남";
    }
    const minutes = Math.floor(diff / 60000);
    const days = Math.floor(minutes / 1440);
    const hours = Math.floor((minutes % 1440) / 60);
    const mins = minutes % 60;
    if (days > 0) {
        return `${days}일 ${hours}시간 남음`;
    }
    if (hours > 0) {
        return `${hours}시간 ${mins}분 남음`;
    }
    return `${Math.max(mins, 1)}분 남음`;
}

export function isPast(iso) {
    return !!iso && new Date(iso).getTime() <= Date.now();
}

export const CATEGORIES = [
    ["CLOTHING_ACCESSORIES", "의류·잡화"],
    ["LIVING", "생활용품"],
    ["ELECTRONICS", "전자기기"],
    ["BOOKS_HOBBY", "도서·취미"],
    ["ETC", "기타"]
];

const LABELS = {
    category: Object.fromEntries(CATEGORIES),
    productStatus: { ON_SALE: "판매 중", SOLD: "판매 종료" },
    offerStatus: { PENDING: "응답 대기", ACCEPTED: "수락됨", REJECTED: "거절됨" },
    tradeStatus: { IN_PROGRESS: "거래 진행 중", ON_HOLD: "환불 요청 처리 중", COMPLETED: "거래 완료", REFUNDED: "환불 완료", CANCELLED: "주문 취소" },
    shippingStatus: { WAITING_SHIPMENT: "발송 대기", SHIPPING: "배송 중", DELIVERED: "배송 완료" },
    priceSource: { LISTED: "등록 가격", AGREED: "합의 가격" },
    completionCause: { BUYER_CONFIRMED: "구매자가 구매를 확정했습니다.", AUTO_EXPIRED: "상품 확인 기간이 지나 자동으로 완료됐습니다.", REFUND_REJECTED: "판매자가 환불 요청을 거절해 거래가 완료됐습니다." },
    cancelledBy: { BUYER: "구매자", SELLER: "판매자", SYSTEM: "시스템(발송 기한 경과)" },
    systemCancelReason: { SHIPMENT_DEADLINE_EXPIRED: "발송 기한까지 발송 정보가 등록되지 않아 자동으로 취소됐습니다." },
    refundDecision: { PENDING: "판매자 응답 대기", APPROVED: "판매자 승인", REJECTED: "판매자 거절", AUTO_APPROVED: "응답 기한 경과로 자동 승인" },
    refundReason: { DESCRIPTION_MISMATCH: "상품 설명과 실제 상태가 다름" },
    transactionType: { CHARGE: "충전", PURCHASE: "구매 결제", CANCEL_REFUND: "주문 취소 반환", REFUND: "환불", SALE_PAYOUT: "판매대금 지급" },
    role: { BUYER: "구매자", SELLER: "판매자" },
    readOnlyReason: {
        NO_ACTIVE_ORDER: "판매가 종료되어 읽기 전용 대화입니다.",
        TRADE_FINALIZED: "이 대화의 거래가 모두 끝나 읽기 전용 대화입니다."
    }
};

const TONES = {
    productStatus: { ON_SALE: "success", SOLD: "outline" },
    offerStatus: { PENDING: "warn", ACCEPTED: "success", REJECTED: "outline" },
    tradeStatus: { IN_PROGRESS: "accent", ON_HOLD: "warn", COMPLETED: "success", REFUNDED: "danger", CANCELLED: "outline" },
    shippingStatus: { WAITING_SHIPMENT: "warn", SHIPPING: "accent", DELIVERED: "success" },
    priceSource: { LISTED: "outline", AGREED: "accent" },
    transactionType: { CHARGE: "accent", PURCHASE: "outline", CANCEL_REFUND: "warn", REFUND: "warn", SALE_PAYOUT: "success" }
};

export function label(kind, value) {
    return (LABELS[kind] && LABELS[kind][value]) || value || "-";
}

export function tone(kind, value) {
    return (TONES[kind] && TONES[kind][value]) || "";
}
