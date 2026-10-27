/*
 * 상품별 1:1 대화 화면(F-005~F-009).
 * - 메시지·가격 제안·제안 응답은 REST(/api)로 보내고, WebSocket(/ws/conversations)은 서버가 보내는 이벤트를 받는 데만 쓴다.
 * - 보낸 사람은 REST 응답으로 결과를 받으므로 자기 MESSAGE 이벤트를 받지 않는다.
 * - 연결이 끊긴 동안 놓친 내용은 다시 연결한 뒤 afterId 메시지 조회와 대화 상세 조회로 가져온다.
 * - 메시지와 제안은 시각순으로 한 타임라인에 보여 준다(설계 6.7). 화면에 넣는 문자열은 모두 텍스트 노드로 넣는다.
 */
import { api, ApiError, CONTEXT_PATH, requestIdHolder } from "./api.js";
import { h, badge, emptyState, toast, toastError, confirmDialog, withBusy, numberValue } from "./dom.js";
import { formatWon, formatNumber, formatDateTime, formatDay, dayKey, formatTime, timeLeft, label, tone, multiplyAmount } from "./format.js";
import { session, links, renderHeader, setActiveNav, refreshBalance, setNickname } from "./shell.js";

const MAX_RECONNECT = 5;
const RECONNECT_DELAY_MS = 3000;

const state = {
    conversationId: null,
    detail: null,
    messages: [],
    messageIds: new Set(),
    // 재연결 따라잡기 기준. 메시지 조회(REST) 결과로만 올린다.
    // 내가 보낸 메시지나 받은 이벤트로 올리면, 끊긴 동안 저장됐거나 전달에 실패한(J-15) 앞선 메시지를 건너뛴다.
    // 다시 받은 메시지는 addMessage 가 messageId 로 거른다.
    syncedMessageId: null,
    socket: null,
    reconnectAttempts: 0,
    reconnecting: false,
    replaced: false,
    sending: false,
    offering: false
};

const dom = {
    convo: document.getElementById("convo"),
    list: document.getElementById("conversationList"),
    thread: document.getElementById("thread"),
    info: document.getElementById("info")
};

/* ------------------------------------------------------------------ 시작 */

async function init() {
    renderHeader(document.getElementById("header"));
    setActiveNav("conversations");
    const id = new URLSearchParams(window.location.search).get("conversationId");
    state.conversationId = /^\d+$/.test(id || "") ? Number(id) : null;

    try {
        await refreshBalance();
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            return;
        }
    }
    await loadList();

    if (state.conversationId === null) {
        dom.thread.replaceChildren(h("div", { class: "convo-empty" },
            emptyState("대화를 선택하세요", "상품 화면의 '판매자에게 문의하기'로 새 대화를 시작할 수 있습니다.",
                h("a", { class: "btn btn--primary", href: links.products }, "상품 둘러보기"))));
        dom.info.replaceChildren(infoGuide());
        return;
    }
    dom.convo.classList.add("has-current");
    buildThread();
    const loaded = await refreshDetail();
    if (!loaded) {
        return;
    }
    await loadMessages(null);
    connect();
}

/* ------------------------------------------------------------------ 대화 목록 */

async function loadList() {
    try {
        const conversations = await api("/conversations");
        if (conversations.length === 0) {
            dom.list.replaceChildren(h("li", { class: "text-muted text-small", style: "padding: 14px;" }, "아직 대화가 없습니다."));
            return;
        }
        dom.list.replaceChildren(...conversations.map((c) => h("li", null,
            h("a", {
                class: ["convo-item", c.conversationId === state.conversationId ? "is-current" : null],
                href: links.conversation(c.conversationId),
                attrs: { "aria-current": c.conversationId === state.conversationId ? "page" : null }
            },
                h("div", { class: "convo-item__top" },
                    h("span", { class: "convo-item__name" }, c.product.name),
                    badge(c.myRole === "BUYER" ? "구매 문의" : "판매", c.myRole === "BUYER" ? "accent" : "success")),
                h("div", { class: "convo-item__sub" },
                    h("span", null, c.counterpartNickname),
                    h("span", null, "·"),
                    h("span", null, label("productStatus", c.product.status)),
                    c.writable ? null : badge("읽기 전용", "outline"))))));
    } catch (error) {
        toastError(error);
    }
}

function infoGuide() {
    return h("div", { class: "convo__scroll", style: "padding: 18px;" },
        h("h2", { class: "panel__title" }, "대화 안내"),
        h("div", { class: "stack-sm text-small text-muted" },
            h("p", null, "상품마다 구매 희망자와 판매자가 1:1로 대화합니다."),
            h("p", null, "구매 희망자는 대화에서 개당 가격과 수량을 제안할 수 있고, 판매자가 수락하면 제안 수량이 예약되며 상품 화면에서 그 가격으로 구매할 수 있습니다."),
            h("p", null, "판매가 끝나면 진행 중인 거래가 있는 대화만 계속 쓸 수 있고, 나머지는 읽기 전용이 됩니다.")));
}

/* ------------------------------------------------------------------ 대화 화면 골격 */

function buildThread() {
    dom.title = h("a", { class: "thread-head__title", href: "#" }, "불러오는 중...");
    dom.sub = h("div", { class: "thread-head__sub" });
    dom.conn = h("span", { class: "conn conn--wait" }, "연결 중");
    dom.inlineInfo = h("div", { class: "thread-info-inline" });
    // 타임라인은 다시 그릴 때 통째로 바뀌므로 live 영역으로 두지 않고, 새로 받은 내용만 별도 영역으로 알린다.
    dom.timeline = h("div", { class: "timeline", attrs: { role: "region", "aria-label": "메시지와 가격 제안", tabindex: "0" } });
    dom.live = h("div", { class: "sr-only", attrs: { "aria-live": "polite" } });
    dom.notice = h("p", { class: "notice notice--warn", hidden: true, style: "margin: 0 12px 0;" });

    dom.input = h("textarea", { class: "input", rows: 1, maxLength: 1000, placeholder: "메시지를 입력하세요", attrs: { "aria-label": "메시지" } });
    dom.send = h("button", { class: "btn btn--primary", type: "button" }, "전송");
    dom.counter = h("span", null, "0 / 1000");

    // 가격 제안 입력은 한 번만 만들고 상태에 따라 보이기만 바꾼다. 다시 만들면 입력 중인 금액과 수량이 지워진다.
    dom.offerNotice = h("p", { class: "notice", hidden: true });
    dom.offerAmount = h("input", { class: "input", type: "number", min: "1", step: "1", inputMode: "numeric", placeholder: "개당 제안 금액(원)", attrs: { "aria-label": "개당 제안 금액" } });
    // 수량의 상한은 대화 상세의 구매 가능 수량이며 입력 편의를 위한 값이다. 허용 여부는 서버의 제안 검사로 정해진다.
    dom.offerQuantity = h("input", { class: "input", type: "number", min: "1", step: "1", value: "1", inputMode: "numeric", placeholder: "수량", style: "max-width: 96px;", attrs: { "aria-label": "제안 수량" } });
    dom.offerButton = h("button", { class: "btn btn--soft", type: "button" }, "가격 제안");
    dom.offerForm = h("div", { class: "composer__offer" }, dom.offerAmount, dom.offerQuantity, dom.offerButton);
    dom.offerRow = h("div", { class: "stack-sm", hidden: true }, dom.offerNotice, dom.offerForm);
    const offerRequestId = requestIdHolder();
    dom.offerButton.addEventListener("click", () => submitOffer(offerRequestId));

    const composer = h("div", { class: "composer" },
        dom.offerRow,
        h("div", { class: "composer__row" }, dom.input, dom.send),
        h("div", { class: "composer__meta" }, h("span", null, "Enter 전송 · Shift+Enter 줄바꿈"), dom.counter));
    dom.composer = composer;

    dom.thread.replaceChildren(
        h("div", { class: "thread-head" },
            h("div", { class: "row" },
                h("a", { class: "convo-back btn btn--ghost btn--sm", href: links.conversations }, "← 목록"),
                dom.title),
            h("div", { class: "row" }, dom.sub, dom.conn)),
        dom.inlineInfo,
        dom.timeline,
        dom.live,
        dom.notice,
        composer);

    dom.send.addEventListener("click", sendMessage);
    dom.input.addEventListener("keydown", (event) => {
        // 한글 입력 조합 중의 Enter 는 글자 확정이므로 전송하지 않는다.
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            sendMessage();
        }
    });
    dom.input.addEventListener("input", () => {
        dom.counter.textContent = dom.input.value.length + " / 1000";
        dom.input.style.height = "auto";
        dom.input.style.height = Math.min(dom.input.scrollHeight, 140) + "px";
    });
}

/* ------------------------------------------------------------------ 대화 상세와 상태 */

async function refreshDetail() {
    try {
        state.detail = await api("/conversations/" + state.conversationId);
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            return false;
        }
        if (error instanceof ApiError && (error.code === "CONVERSATION_NOT_FOUND" || error.code === "NOT_CONVERSATION_MEMBER")) {
            dom.thread.replaceChildren(h("div", { class: "convo-empty" },
                emptyState("대화를 열 수 없습니다", error.message,
                    h("a", { class: "btn btn--ghost", href: links.conversations }, "내 대화 목록"))));
            dom.info.replaceChildren(infoGuide());
            return false;
        }
        toastError(error);
        return false;
    }
    const detail = state.detail;
    setNickname(myNickname());
    document.title = detail.product.name + " · 대화 · C2C Marketplace";
    dom.title.textContent = detail.product.name;
    renderState();
    renderTimeline(false);
    renderInfo();
    return true;
}

function myNickname() {
    const detail = state.detail;
    return detail.myRole === "BUYER" ? detail.buyer.nickname : detail.seller.nickname;
}

function counterpartNickname() {
    const detail = state.detail;
    return detail.myRole === "BUYER" ? detail.seller.nickname : detail.buyer.nickname;
}

/* 상품 상태·남은 수량·구매 가능 수량·쓰기 가능 여부. 대화 상세와 CONVERSATION_STATE 이벤트가 함께 쓴다. */
function applyStateEvent(event) {
    if (!state.detail) {
        return;
    }
    state.detail.product.status = event.productStatus;
    state.detail.product.remainingQuantity = event.remainingQuantity;
    state.detail.product.availableQuantity = event.availableQuantity;
    state.detail.writable = event.writable;
    state.detail.readOnlyReason = event.readOnlyReason;
    renderState();
}

/* 수락된 제안 가운데 합의 상태가 statuses 에 드는 가장 최근 것. 수락된 제안은 합의가 만료·해제된 뒤 새로 생길 수 있다. */
function agreementOffer(statuses) {
    const offers = (state.detail.offers || []).filter((offer) => offer.status === "ACCEPTED" && statuses.includes(offer.agreementStatus));
    return offers.length > 0 ? offers[offers.length - 1] : null;
}

/* 합의가 예약 중인 제안. 합의 가격 구매 연결과 버튼은 이 제안이 있을 때만 보인다. */
function reservedOffer() {
    return agreementOffer(["RESERVED"]);
}

/* 새 제안을 막는 제안: 응답 대기 제안, 또는 합의가 예약 중이거나 결제됨인 수락 제안(F-008). */
function openOffer() {
    return (state.detail.offers || []).find((offer) => offer.status === "PENDING") || agreementOffer(["RESERVED", "ORDERED"]);
}

/* 「개당 8,000원 × 3개」 */
function offerTerms(offer) {
    return "개당 " + formatWon(offer.amount) + " × " + formatNumber(offer.quantity) + "개";
}

function renderState() {
    const detail = state.detail;
    const product = detail.product;
    // 판매가 끝난 상품은 공개 상세가 없으므로(F-002) 제목을 상품 화면으로 연결하지 않는다.
    if (product.status === "ON_SALE") {
        dom.title.href = links.product(product.productId);
    } else {
        dom.title.removeAttribute("href");
    }
    // 구매 희망자에게는 다른 회원의 예약을 뺀 구매 가능 수량을 함께 보인다. 판매자에게 오는 값은 더 걸 수 있는 예약 수량이라 여기에는 보이지 않는다.
    const isBuyer = detail.myRole === "BUYER";
    dom.sub.replaceChildren(
        badge(label("productStatus", product.status), tone("productStatus", product.status)),
        h("span", null, formatWon(product.price)),
        h("span", null, "·"),
        h("span", null, "남은 " + formatNumber(product.remainingQuantity) + "개"),
        isBuyer ? h("span", null, "·") : null,
        isBuyer ? h("span", null, "구매 가능 " + formatNumber(product.availableQuantity) + "개") : null,
        h("span", null, "·"),
        h("span", null, (isBuyer ? "판매자 " : "구매 희망자 ") + counterpartNickname()));

    dom.input.disabled = !detail.writable;
    dom.send.disabled = !detail.writable || state.sending;
    if (!detail.writable) {
        showNotice(label("readOnlyReason", detail.readOnlyReason));
    } else if (!state.replaced) {
        hideNotice();
    }

    // 가격 제안 입력: 구매 희망자이고, 쓰기 가능하고, 판매 중이며, 응답 대기 제안과 예약 중·결제됨인 합의가 없을 때만 보인다(F-008).
    const open = openOffer();
    const offerArea = detail.myRole === "BUYER" && product.status === "ON_SALE" && detail.writable;
    dom.offerRow.hidden = !offerArea;
    if (!offerArea) {
        return;
    }
    dom.offerNotice.hidden = !open;
    dom.offerForm.hidden = !!open;
    dom.offerButton.disabled = state.offering;
    dom.offerQuantity.max = String(Math.max(product.availableQuantity, 1));
    if (open && open.status === "PENDING") {
        dom.offerNotice.className = "notice notice--info";
        dom.offerNotice.replaceChildren(offerTerms(open) + " 제안에 대한 판매자의 응답을 기다리고 있습니다.");
    } else if (open && open.agreementStatus === "RESERVED") {
        dom.offerNotice.className = "notice notice--success";
        dom.offerNotice.replaceChildren("합의 가격 " + offerTerms(open) + "가 정해졌습니다. ",
            h("a", { href: links.product(product.productId) + "?offer=" + open.offerId }, "이 가격으로 구매하기"));
    } else if (open && open.agreementStatus === "ORDERED") {
        dom.offerNotice.className = "notice notice--info";
        dom.offerNotice.replaceChildren("합의 가격 " + offerTerms(open) + "로 결제한 주문이 진행 중입니다. "
            + "그 주문이 정상 완료되거나 환불되면 새 가격을 제안할 수 있습니다.");
    }
}

async function submitOffer(requestId) {
    if (state.offering) {
        return;
    }
    state.offering = true;
    dom.offerButton.disabled = true;
    const raw = dom.offerAmount.value.trim();
    try {
        await api("/conversations/" + state.conversationId + "/offers", {
            method: "POST",
            body: { amount: raw === "" ? null : Number(raw), quantity: numberValue(dom.offerQuantity), requestId: requestId.value }
        });
        requestId.settle(null);
        dom.offerAmount.value = "";
        dom.offerQuantity.value = "1";
        toast("가격을 제안했습니다.", "success");
        await refreshDetail();
    } catch (error) {
        requestId.settle(error);
        const fieldErrors = (error instanceof ApiError && error.fieldErrors) || {};
        toast(fieldErrors.amount || fieldErrors.quantity || error.message, "error");
        if (error instanceof ApiError && error.status === 409) {
            await refreshDetail();
        }
    } finally {
        state.offering = false;
        if (state.detail) {
            renderState();
        }
    }
}

function showNotice(text) {
    dom.notice.textContent = text;
    dom.notice.hidden = false;
}

function hideNotice() {
    dom.notice.hidden = true;
    dom.notice.textContent = "";
}

/* ------------------------------------------------------------------ 오른쪽 정보 */

function renderInfo() {
    const detail = state.detail;
    const product = detail.product;
    const isBuyer = detail.myRole === "BUYER";
    // 합의 가격 구매 버튼은 합의가 예약 중일 때만 보인다. 정보의 「합의 가격」 행은 합의가 예약 중이거나 결제됨일 때 보인다.
    const reserved = reservedOffer();
    const agreement = agreementOffer(["RESERVED", "ORDERED"]);

    let cta = null;
    if (isBuyer && product.status === "ON_SALE") {
        cta = reserved
            ? h("a", { class: "btn btn--primary btn--block", href: links.product(product.productId) + "?offer=" + reserved.offerId }, "합의 가격 " + formatWon(reserved.amount) + "으로 구매")
            : h("a", { class: "btn btn--primary btn--block", href: links.product(product.productId) }, "구매하기");
    }

    const orders = detail.orders || [];
    const orderList = orders.length === 0
        ? h("p", { class: "text-muted text-small" }, isBuyer ? "이 상품의 주문이 아직 없습니다." : "이 구매 희망자의 주문이 아직 없습니다.")
        : h("div", { class: "rows" }, orders.map((order) => h("a", { class: "list-row", href: links.order(order.orderId) },
            h("div", { class: "stack-sm" },
                h("div", { class: "text-strong" }, "주문 #" + order.orderId),
                h("div", { class: "list-row__sub" }, formatNumber(order.quantity) + "개 × " + formatWon(order.unitPrice)),
                h("div", { class: "badges" },
                    badge(label("tradeStatus", order.tradeStatus), tone("tradeStatus", order.tradeStatus)),
                    badge(label("shippingStatus", order.shippingStatus), tone("shippingStatus", order.shippingStatus)))),
            h("div", { class: "list-row__amount" }, formatWon(order.paidAmount)))));

    dom.info.replaceChildren(h("div", { class: "convo__scroll", style: "padding: 18px;" },
        h("div", { class: "stack-md" },
            h("div", { class: "badges" },
                badge(label("category", product.category), "accent"),
                badge(label("productStatus", product.status), tone("productStatus", product.status))),
            product.status === "ON_SALE"
                ? h("a", { class: "text-strong", href: links.product(product.productId), style: "font-size: 1.1rem; color: var(--text);" }, product.name)
                : h("span", { class: "text-strong", style: "font-size: 1.1rem;" }, product.name),
            h("dl", { class: "kv kv--compact" },
                h("dt", null, "등록 가격"), h("dd", null, formatWon(product.price)),
                h("dt", null, "남은 수량"), h("dd", null, formatNumber(product.remainingQuantity) + "개"),
                isBuyer ? h("dt", null, "구매 가능 수량") : null,
                isBuyer ? h("dd", null, formatNumber(product.availableQuantity) + "개") : null,
                agreement ? h("dt", null, "합의 가격") : null,
                agreement ? h("dd", null, offerTerms(agreement) + " ",
                    badge(label("agreementStatus", agreement.agreementStatus), tone("agreementStatus", agreement.agreementStatus))) : null,
                h("dt", null, "판매자"), h("dd", null, detail.seller.nickname),
                h("dt", null, "구매 희망자"), h("dd", null, detail.buyer.nickname)),
            cta,
            isBuyer ? null : h("p", { class: "text-muted text-small" }, "구매 희망자의 가격 제안은 대화에서 수락하거나 거절할 수 있습니다. 수락한 가격은 이 구매 희망자에게만 적용됩니다. 수락하면 제안 수량이 이 구매 희망자에게 예약됩니다."),
            h("hr", { class: "divider", style: "margin: 4px 0;" }),
            reservationCard(),
            h("hr", { class: "divider", style: "margin: 4px 0;" }),
            h("h2", { class: "panel__title", style: "margin: 0;" }, "이 대화의 주문"),
            orderList)));

    dom.inlineInfo.replaceChildren(h("div", { class: "row row--between" },
        h("span", { class: "text-small text-muted" }, reserved ? "합의 가격 " + formatWon(reserved.amount) : "등록 가격 " + formatWon(product.price)),
        h("div", { class: "row" },
            orders.length > 0 ? h("a", { class: "btn btn--ghost btn--sm", href: links.order(orders[0].orderId) }, "주문 보기") : null,
            cta ? h("a", { class: "btn btn--primary btn--sm", href: cta.href }, "구매하기") : null)));
}

/* ------------------------------------------------------------------ 예약 */

/*
 * 대화 상세의 reservation(이 상품·구매 희망자의 가장 최근 예약 하나)을 보여 준다. 활성이면 판매자와 구매 희망자 모두 해제할 수 있다.
 * 예약은 판매자가 가격 제안을 수락할 때 제안 수량만큼 걸리므로 예약을 거는 버튼은 없다.
 * 버튼 노출은 화면 편의를 위한 판단이며 허용 여부는 서버의 검사로 정해진다.
 */
function reservationCard() {
    const detail = state.detail;
    const reservation = detail.reservation;
    const isSeller = detail.myRole === "SELLER";
    const active = !!reservation && reservation.status === "ACTIVE";
    const nodes = [h("h2", { class: "panel__title", style: "margin: 0;" }, "예약")];
    if (reservation) {
        const rows = [
            h("dt", null, "상태"), h("dd", null, badge(label("reservationStatus", reservation.status), tone("reservationStatus", reservation.status))),
            h("dt", null, "예약 수량"), h("dd", null, formatNumber(reservation.quantity) + "개"),
            h("dt", null, "만료 시각"), h("dd", null, formatDateTime(reservation.expiresAt) + (active ? " (" + timeLeft(reservation.expiresAt) + ")" : ""))
        ];
        if (reservation.status === "RELEASED") {
            rows.push(h("dt", null, "해제한 쪽"), h("dd", null, label("releasedBy", reservation.releasedBy)));
        }
        if (reservation.status === "FULFILLED" && reservation.fulfilledOrderId) {
            rows.push(h("dt", null, "결제 주문"), h("dd", null, h("a", { href: links.order(reservation.fulfilledOrderId) }, "주문 #" + reservation.fulfilledOrderId)));
        }
        if (reservation.endedAt) {
            rows.push(h("dt", null, "끝난 시각"), h("dd", null, formatDateTime(reservation.endedAt)));
        }
        nodes.push(h("dl", { class: "kv kv--compact" }, rows));
    } else {
        nodes.push(h("p", { class: "text-muted text-small" }, isSeller
            ? "구매 희망자의 가격 제안을 수락하면 제안 수량이 예약됩니다."
            : "판매자가 가격 제안을 수락하면 제안 수량이 예약됩니다."));
    }
    if (active) {
        nodes.push(h("p", { class: "text-muted text-small" }, isSeller
            ? "예약 중에는 이 구매 희망자만 예약 수량을 살 수 있습니다. 만료 시각이 지나면 자동으로 해제됩니다."
            : "만료 시각까지 합의 가격으로 예약 수량을 결제할 수 있습니다. 결제하면 예약이 끝납니다."));
        nodes.push(releaseButton(reservation));
    }
    return h("div", { class: "stack-sm" }, nodes);
}

function releaseButton(reservation) {
    const isSeller = state.detail.myRole === "SELLER";
    const button = h("button", { class: "btn btn--ghost btn--block", type: "button" }, "예약 해제");
    button.addEventListener("click", () => withBusy(button, async () => {
        const ok = await confirmDialog({
            title: "예약을 해제할까요?",
            message: isSeller
                ? "해제하면 다른 회원도 이 수량을 살 수 있습니다. 해제된 합의로는 결제할 수 없습니다. 구매 희망자가 새 제안을 보내고 판매자가 수락하면 다시 예약됩니다."
                : "해제하면 예약 수량의 보장이 없어지고 다른 회원도 이 수량을 살 수 있습니다. 해제된 합의로는 결제할 수 없습니다.",
            confirmLabel: "예약 해제",
            danger: true
        });
        if (!ok) {
            return;
        }
        try {
            await api("/reservations/" + reservation.reservationId, { method: "DELETE" });
            toast("예약을 해제했습니다.", "success");
        } catch (error) {
            toastError(error);
        }
        await refreshDetail();
    }));
    return button;
}

/* ------------------------------------------------------------------ 메시지·타임라인 */

async function loadMessages(afterId) {
    try {
        const query = afterId ? "?afterId=" + afterId : "";
        const messages = await api("/conversations/" + state.conversationId + "/messages" + query);
        messages.forEach((message) => {
            addMessage(message);
            markSynced(message.messageId);
        });
        renderTimeline(true);
    } catch (error) {
        toastError(error);
    }
}

/* 같은 messageId 를 두 번 넣지 않는다. REST 응답·이벤트·재접속 조회가 같은 메시지를 줄 수 있기 때문이다. */
function addMessage(message) {
    if (state.messageIds.has(message.messageId)) {
        return;
    }
    state.messageIds.add(message.messageId);
    state.messages.push(message);
}

function markSynced(messageId) {
    if (state.syncedMessageId === null || messageId > state.syncedMessageId) {
        state.syncedMessageId = messageId;
    }
}

function isMine(message) {
    if (session.memberId) {
        return message.senderId === session.memberId;
    }
    return state.detail && message.senderNickname === myNickname();
}

function renderTimeline(forceBottom) {
    if (!dom.timeline || !state.detail) {
        return;
    }
    const timeline = dom.timeline;
    const nearBottom = timeline.scrollHeight - timeline.scrollTop - timeline.clientHeight < 80;

    const entries = state.messages.map((message) => ({ time: message.createdAt, order: message.messageId, kind: "message", data: message }));
    (state.detail.offers || []).forEach((offer) => {
        entries.push({ time: offer.createdAt, order: offer.offerId, kind: "offer", data: offer });
        if (offer.respondedAt) {
            entries.push({ time: offer.respondedAt, order: offer.offerId, kind: "response", data: offer });
        }
    });
    entries.sort((a, b) => {
        const diff = new Date(a.time).getTime() - new Date(b.time).getTime();
        return diff !== 0 ? diff : a.order - b.order;
    });

    if (entries.length === 0) {
        timeline.replaceChildren(h("div", { class: "convo-empty" },
            h("p", null, state.detail.myRole === "BUYER"
                ? "판매자에게 궁금한 점을 물어보거나 개당 가격과 수량을 제안해 보세요."
                : "구매 희망자의 메시지가 아직 없습니다.")));
        return;
    }

    const nodes = [];
    let lastDay = null;
    entries.forEach((entry) => {
        const day = dayKey(entry.time);
        if (day !== lastDay) {
            nodes.push(h("div", { class: "timeline__day" }, formatDay(entry.time)));
            lastDay = day;
        }
        nodes.push(entry.kind === "message" ? messageNode(entry.data) : offerNode(entry.data, entry.kind));
    });
    timeline.replaceChildren(...nodes);
    if (forceBottom || nearBottom) {
        timeline.scrollTop = timeline.scrollHeight;
    }
}

function messageNode(message) {
    const mine = isMine(message);
    return h("div", { class: ["bubble-row", mine ? "bubble-row--mine" : null] },
        mine ? null : h("span", { class: "bubble-row__name" }, message.senderNickname),
        h("div", { class: "bubble" }, message.content),
        h("span", { class: "bubble-row__time" }, formatTime(message.createdAt)));
}

function offerNode(offer, kind) {
    const detail = state.detail;
    if (kind === "response") {
        const accepted = offer.status === "ACCEPTED";
        return h("div", { class: "offer-card offer-card--response" },
            h("span", { class: "offer-card__label" }, "제안 응답"),
            h("span", null, "판매자가 " + offerTerms(offer) + " 제안을 " + (accepted ? "수락했습니다." : "거절했습니다.")),
            // 합의 가격으로 구매하는 연결은 합의가 예약 중일 때만 보인다.
            offer.agreementStatus === "RESERVED" && detail.myRole === "BUYER" && detail.product.status === "ON_SALE"
                ? h("a", { class: "text-small", href: links.product(detail.product.productId) + "?offer=" + offer.offerId }, "합의 가격으로 구매하기")
                : null,
            h("span", { class: "offer-card__meta" }, formatTime(offer.respondedAt)));
    }
    const canRespond = offer.status === "PENDING" && detail.myRole === "SELLER" && detail.product.status === "ON_SALE" && detail.writable;
    // 판매가 끝나면 대기 제안은 상태가 그대로 남지만 더 응답할 수 없는 과거 내역이다(F-009 추가 규칙).
    const closed = offer.status === "PENDING" && detail.product.status !== "ON_SALE";
    return h("div", { class: "offer-card" },
        h("span", { class: "offer-card__label" }, "가격 제안"),
        h("span", { class: "offer-card__amount" }, offerTerms(offer) + " = " + formatWon(multiplyAmount(offer.amount, offer.quantity))),
        h("div", { class: "badges", style: "justify-content: center;" },
            closed
                ? badge("판매 종료로 응답 불가", "outline")
                : badge(label("offerStatus", offer.status), tone("offerStatus", offer.status)),
            // 수락된 제안에는 합의 상태(예약 중, 결제됨, 만료, 해제)를 함께 보인다.
            offer.status === "ACCEPTED" && offer.agreementStatus
                ? badge(label("agreementStatus", offer.agreementStatus), tone("agreementStatus", offer.agreementStatus))
                : null,
            h("span", { class: "offer-card__meta" }, detail.buyer.nickname + " · " + formatTime(offer.createdAt))),
        canRespond ? h("div", { class: "actions" },
            offerButton("수락", "btn--primary", offer, "accept"),
            offerButton("거절", "btn--ghost", offer, "reject")) : null);
}

function offerButton(text, style, offer, action) {
    const button = h("button", { class: ["btn", "btn--sm", style], type: "button" }, text);
    button.addEventListener("click", () => withBusy(button, async () => {
        const accepting = action === "accept";
        const quantity = formatNumber(offer.quantity) + "개";
        const ok = await confirmDialog({
            title: accepting ? "가격 제안을 수락할까요?" : "가격 제안을 거절할까요?",
            message: accepting
                ? offerTerms(offer) + "가 이 구매 희망자의 합의 내용이 되고, " + quantity + "가 이 구매 희망자에게 예약됩니다. "
                    + "예약 기간이 지나면 자동으로 해제됩니다. 수락한 뒤에는 철회할 수 없습니다."
                : "거절한 뒤에도 구매 희망자는 새 가격을 제안할 수 있습니다.",
            confirmLabel: accepting ? "수락" : "거절",
            danger: !accepting
        });
        if (!ok) {
            return;
        }
        // 수락과 거절은 본문 없이 보낸다. 수락하면 서버가 같은 트랜잭션에서 제안 수량만큼 예약을 건다.
        try {
            await api("/offers/" + offer.offerId + "/" + action, { method: "POST" });
            toast(accepting ? "제안을 수락했습니다. 제안 수량이 예약되었습니다." : "제안을 거절했습니다.", "success");
        } catch (error) {
            toastError(error);
        }
        await refreshDetail();
    }));
    return button;
}

/*
 * 전송 중에는 state.sending 으로 버튼을 잠근다. 쓰기 가능 여부도 renderState 가 같은 버튼을 바꾸므로,
 * 두 조건을 함께 계산해 전송이 끝난 뒤 읽기 전용 대화의 버튼이 다시 켜지지 않게 한다.
 */
async function sendMessage() {
    if (state.sending || !state.detail || !state.detail.writable) {
        return;
    }
    state.sending = true;
    dom.send.disabled = true;
    try {
        await submitMessage();
    } finally {
        state.sending = false;
        if (state.detail) {
            dom.send.disabled = !state.detail.writable;
        }
    }
}

async function submitMessage() {
    const content = dom.input.value;
    if (!content.trim()) {
        return;
    }
    try {
        const saved = await api("/conversations/" + state.conversationId + "/messages", {
            method: "POST",
            body: { content }
        });
        dom.input.value = "";
        dom.input.dispatchEvent(new Event("input"));
        addMessage({ ...saved, senderNickname: myNickname() });
        renderTimeline(true);
    } catch (error) {
        const fieldMessage = error instanceof ApiError && error.fieldErrors && error.fieldErrors.content;
        toast(fieldMessage || error.message, "error");
        if (error instanceof ApiError && error.code === "CONVERSATION_READ_ONLY") {
            await refreshDetail();
        }
    } finally {
        dom.input.focus();
    }
}

/* ------------------------------------------------------------------ 실시간 연결 */

function setConnection(kind, text) {
    dom.conn.className = "conn conn--" + kind;
    dom.conn.textContent = text;
}

function connect() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${protocol}//${window.location.host}${CONTEXT_PATH}/ws/conversations?conversationId=${state.conversationId}`;
    setConnection("wait", state.reconnecting ? "다시 연결 중" : "연결 중");
    const socket = new WebSocket(url);
    state.socket = socket;
    socket.onopen = () => onOpen();
    socket.onmessage = (event) => onMessage(event);
    socket.onclose = (event) => onClose(event);
}

/*
 * 연결될 때마다(첫 연결 포함) 마지막으로 받은 메시지 이후의 메시지와 최신 상태를 다시 조회한다.
 * 첫 조회와 연결 사이, 또는 끊긴 동안 저장된 내용은 이벤트로 오지 않기 때문이다. 같은 메시지는 addMessage 가 거른다.
 */
async function onOpen() {
    setConnection("on", "실시간 연결됨");
    state.reconnecting = false;
    state.reconnectAttempts = 0;
    await loadMessages(state.syncedMessageId);
    await refreshDetail();
}

function announce(text) {
    dom.live.textContent = text;
}

async function onMessage(event) {
    let data;
    try {
        data = JSON.parse(event.data);
    } catch (e) {
        console.error("이벤트 JSON 파싱 실패", e);
        return;
    }
    switch (data.type) {
        case "MESSAGE":
            addMessage(data);
            renderTimeline(false);
            announce(data.senderNickname + ": " + data.content);
            break;
        case "OFFER":
            announce("가격 제안 " + offerTerms(data) + " · " + label("offerStatus", data.status));
            await refreshDetail();
            break;
        case "CONVERSATION_STATE":
            applyStateEvent(data);
            // 주문 요약은 이벤트에 없으므로 상세와 목록을 다시 조회한다.
            await refreshDetail();
            await loadList();
            break;
        default:
            console.warn("알 수 없는 이벤트 타입:", data.type);
    }
}

function onClose(event) {
    if (event.code === 3000) {
        // 같은 대화를 다른 탭에서 열면 서버가 이전 연결을 3000 으로 닫는다.
        state.replaced = true;
        setConnection("off", "다른 탭에서 연결됨");
        showNotice("다른 탭에서 이 대화를 열어 이 화면의 실시간 연결이 끊겼습니다. 새로고침하면 이 화면으로 다시 연결합니다.");
        return;
    }
    if (state.reconnectAttempts >= MAX_RECONNECT) {
        setConnection("off", "연결 끊김");
        showNotice("실시간 연결이 끊겼습니다. 새로고침하면 저장된 내용을 다시 불러옵니다.");
        return;
    }
    state.reconnectAttempts++;
    state.reconnecting = true;
    setConnection("wait", "다시 연결 중");
    window.setTimeout(connect, RECONNECT_DELAY_MS);
}

init();
