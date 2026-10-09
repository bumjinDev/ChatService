/*
 * 구매·판매 내역과 주문 상세(F-019), 주문 단계별 요청 화면.
 * 발송 등록(F-011), 취소(F-012), 구매 확정(F-015), 환불 요청(F-017), 환불 승인·거절(F-018)을 이 화면에서 보낸다.
 * 어떤 버튼을 보여 줄지는 상태와 기한을 보고 화면에서 고르지만, 허용 여부와 기한 판정은 서버가 한다.
 */
import { api, ApiError, requestIdHolder } from "../api.js";
import { h, badge, loading, emptyState, errorState, toast, toastError, confirmDialog, field, formError, clearErrors, showErrors, withBusy } from "../dom.js";
import { formatWon, formatNumber, formatDateTime, timeLeft, isPast, label, tone } from "../format.js";
import { links, refreshBalance } from "../shell.js";

export async function orderListView(mount, ctx, kind) {
    const isPurchases = kind === "purchases";
    const tabs = h("nav", { class: "tabs", attrs: { "aria-label": "주문 구분" } },
        h("a", { class: ["tabs__item", isPurchases ? "is-active" : null], href: "#/purchases" }, "구매 내역"),
        h("a", { class: ["tabs__item", isPurchases ? null : "is-active"], href: "#/sales" }, "판매 내역"));
    const body = h("div", null, loading());
    mount.replaceChildren(
        h("div", { class: "page-head" },
            h("div", null,
                h("h1", { class: "page-head__title" }, isPurchases ? "구매 내역" : "판매 내역"),
                h("p", { class: "page-head__sub" }, isPurchases
                    ? "내가 결제한 주문입니다. 주문을 눌러 배송 상태 확인, 구매 확정, 환불 요청을 할 수 있습니다."
                    : "내 상품에 들어온 주문입니다. 주문을 눌러 발송 정보를 등록하거나 환불 요청에 응답합니다.")),
            h("div", { class: "page-head__actions" },
                h("button", { class: "btn btn--ghost", type: "button", onClick: () => ctx.reload() }, "새로고침"))),
        tabs,
        body);

    try {
        const orders = await api(isPurchases ? "/orders/purchases" : "/orders/sales");
        if (orders.length === 0) {
            body.replaceChildren(emptyState(isPurchases ? "구매한 주문이 없습니다" : "들어온 주문이 없습니다",
                isPurchases ? "상품을 둘러보고 마음에 드는 물건을 구매해 보세요." : "상품을 등록하면 구매 희망자의 주문이 여기에 표시됩니다.",
                h("a", { class: "btn btn--primary", href: isPurchases ? "#/products" : "#/sell" }, isPurchases ? "상품 둘러보기" : "상품 판매하기")));
            return;
        }
        body.replaceChildren(h("section", { class: "panel", style: "padding: 6px;" },
            h("div", { class: "rows order-list" }, orders.map(orderRow))));
    } catch (error) {
        body.replaceChildren(errorState(error, () => ctx.reload()));
    }
}

function orderRow(order) {
    return h("a", { class: "list-row", href: "#/orders/" + order.orderId },
        h("div", { class: "stack-sm", style: "min-width: 0;" },
            h("div", { class: "list-row__title" }, order.product.name),
            h("div", { class: "list-row__sub" },
                "주문 #" + order.orderId + " · " + formatDateTime(order.confirmedAt)
                + " · " + formatNumber(order.quantity) + "개 × " + formatWon(order.unitPrice)),
            h("div", { class: "badges" },
                badge(label("tradeStatus", order.tradeStatus), tone("tradeStatus", order.tradeStatus)),
                badge(label("shippingStatus", order.shippingStatus), tone("shippingStatus", order.shippingStatus)),
                badge(label("priceSource", order.priceSource), tone("priceSource", order.priceSource)))),
        h("div", { class: "list-row__side" },
            h("div", { class: "list-row__amount" }, formatWon(order.paidAmount)),
            order.finalizedAt ? h("div", { class: "list-row__sub" }, "종료 " + formatDateTime(order.finalizedAt)) : null));
}

/* ------------------------------------------------------------------ 주문 상세 */

export async function orderDetailView(mount, ctx) {
    const orderId = Number(ctx.params[0]);
    mount.replaceChildren(loading());
    let order;
    try {
        order = await api("/orders/" + orderId);
    } catch (error) {
        if (error instanceof ApiError && (error.code === "ORDER_NOT_FOUND" || error.code === "NOT_TRADE_PARTY")) {
            mount.replaceChildren(emptyState("주문을 볼 수 없습니다", error.message,
                h("a", { class: "btn btn--ghost", href: "#/purchases" }, "구매 내역으로")));
            return;
        }
        mount.replaceChildren(errorState(error, () => ctx.reload()));
        return;
    }
    const conversationLink = await findTradeConversation(order).catch(() => null);
    renderOrder(mount, order, conversationLink, ctx);
}

/* 이 주문의 거래 대화. 구매자는 상품별 내 대화, 판매자는 같은 상품·같은 구매자의 대화를 찾는다. */
async function findTradeConversation(order) {
    const conversations = await api("/conversations");
    const match = conversations.find((c) => c.product.productId === order.product.productId
        && c.myRole === order.myRole
        && (order.myRole === "BUYER" || c.counterpartNickname === order.buyerNickname));
    return match ? links.conversation(match.conversationId) : null;
}

function renderOrder(mount, order, conversationLink, ctx) {
    const isBuyer = order.myRole === "BUYER";
    const counterpart = isBuyer ? order.sellerNickname : order.buyerNickname;
    const rerender = (updated) => renderOrder(mount, updated, conversationLink, ctx);

    mount.replaceChildren(
        h("a", { class: "back-link", href: isBuyer ? "#/purchases" : "#/sales" }, isBuyer ? "← 구매 내역" : "← 판매 내역"),
        h("div", { class: "page-head" },
            h("div", null,
                h("div", { class: "badges", style: "margin-bottom: 8px;" },
                    badge(isBuyer ? "내가 구매자" : "내가 판매자", "accent"),
                    badge(label("tradeStatus", order.tradeStatus), tone("tradeStatus", order.tradeStatus)),
                    badge(label("shippingStatus", order.shippingStatus), tone("shippingStatus", order.shippingStatus))),
                h("h1", { class: "page-head__title" }, "주문 #" + order.orderId),
                h("p", { class: "page-head__sub" },
                    order.product.name + " · " + (isBuyer ? "판매자 " : "구매자 ") + counterpart)),
            h("div", { class: "page-head__actions" },
                conversationLink ? h("a", { class: "btn btn--ghost", href: conversationLink }, "거래 대화") : null,
                h("button", { class: "btn btn--ghost", type: "button", onClick: () => ctx.reload() }, "새로고침"))),
        h("section", { class: "panel" }, stepper(order)),
        h("div", { class: "layout-2col", style: "margin-top: 16px;" },
            h("div", null,
                paymentPanel(order),
                productPanel(order),
                shippingPanel(order),
                order.cancellation ? cancellationPanel(order) : null,
                order.refundRequest ? refundPanel(order) : null,
                ledgerPanel(order)),
            h("aside", null, actionPanel(order, rerender))));
}

function stepper(order) {
    const final = order.tradeStatus;
    const cancelled = final === "CANCELLED";
    const steps = [
        { label: "결제 완료", time: order.confirmedAt, done: true },
        { label: "발송", time: order.shipment && order.shipment.shippedAt, done: !!order.shipment },
        { label: "배송 완료", time: order.deliveredAt, done: !!order.deliveredAt },
        {
            label: order.finalizedAt ? label("tradeStatus", final) : (final === "ON_HOLD" ? "환불 요청 처리 중" : "거래 종료"),
            time: order.finalizedAt,
            done: !!order.finalizedAt
        }
    ];
    if (cancelled) {
        steps[1] = { label: "발송 전 취소", time: order.cancellation && order.cancellation.cancelledAt, stopped: true };
        steps[2] = { label: "배송 없음", time: null, stopped: true };
    }
    const currentIndex = steps.findIndex((step) => !step.done && !step.stopped);
    return h("div", { class: "stepper" }, steps.map((step, index) =>
        h("div", { class: ["step", step.done ? "is-done" : null, step.stopped ? "is-stopped" : null, index === currentIndex ? "is-current" : null] },
            h("div", { class: "step__label" }, step.label),
            h("div", { class: "step__time" }, step.time ? formatDateTime(step.time) : (index === currentIndex ? "진행 예정" : "-")))));
}

function kvRows(rows) {
    return h("dl", { class: "kv" }, rows.filter(Boolean).flatMap(([key, value]) => [h("dt", null, key), h("dd", null, value)]));
}

function deadline(iso) {
    return h("span", null,
        h("span", { class: "deadline" }, formatDateTime(iso)),
        h("span", { class: "deadline__left" }, "(" + timeLeft(iso) + ")"));
}

function paymentPanel(order) {
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "결제 정보"),
        kvRows([
            ["상품", order.product.name],
            ["수량", formatNumber(order.quantity) + "개"],
            ["개당 가격", formatWon(order.unitPrice) + " (" + label("priceSource", order.priceSource) + ")"],
            ["결제 금액", formatWon(order.paidAmount)],
            ["결제 시각", formatDateTime(order.confirmedAt)],
            [order.myRole === "BUYER" ? "판매자" : "구매자", order.myRole === "BUYER" ? order.sellerNickname : order.buyerNickname]
        ]));
}

/* 판매가 끝난 상품은 공개 상세가 없으므로(F-002) 주문 상세에 담긴 상품 정보를 여기서 보여 준다. 환불 사유 판단에 설명이 필요하다. */
function productPanel(order) {
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "상품 정보"),
        kvRows([
            ["카테고리", label("category", order.product.category)],
            ["등록 가격", formatWon(order.product.price)]
        ]),
        h("hr", { class: "divider" }),
        h("p", { class: "product-desc" }, order.product.description));
}

function shippingPanel(order) {
    const shipment = order.shipment;
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "배송 정보"),
        kvRows([
            ["수령인", order.recipientName],
            ["배송 주소", order.shippingAddress],
            ["발송 기한", formatDateTime(order.shipDeadlineAt)],
            shipment ? ["택배사", shipment.carrierName] : null,
            shipment ? ["운송장 번호", shipment.trackingNumber] : null,
            shipment ? ["발송 시각", formatDateTime(shipment.shippedAt)] : null,
            shipment && !order.deliveredAt ? ["배송 완료 예정", formatDateTime(shipment.deliveryDueAt)] : null,
            order.deliveredAt ? ["배송 완료", formatDateTime(order.deliveredAt)] : null,
            order.inspectionDeadlineAt ? ["상품 확인 기한", formatDateTime(order.inspectionDeadlineAt)] : null
        ]));
}

function cancellationPanel(order) {
    const c = order.cancellation;
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "취소 정보"),
        kvRows([
            ["취소한 쪽", label("cancelledBy", c.cancelledBy)],
            // 발송 기한 경과 자동 취소는 서버가 고정 사유 코드를 저장한다(설계 6.13). 당사자가 쓴 사유는 그대로 보여 준다.
            ["취소 사유", c.cancelledBy === "SYSTEM" ? label("systemCancelReason", c.reason) : c.reason],
            ["취소 시각", formatDateTime(c.cancelledAt)]
        ]));
}

function refundPanel(order) {
    const r = order.refundRequest;
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "환불 요청"),
        kvRows([
            ["사유", label("refundReason", r.reasonCode)],
            ["상세 설명", r.detail],
            ["요청 시각", formatDateTime(r.requestedAt)],
            ["응답 기한", formatDateTime(r.responseDeadlineAt)],
            ["처리 결과", label("refundDecision", r.decision)],
            r.decidedAt ? ["처리 시각", formatDateTime(r.decidedAt)] : null
        ]));
}

function ledgerPanel(order) {
    const transactions = order.myBalanceTransactions || [];
    return h("section", { class: "panel" },
        h("h2", { class: "panel__title" }, "이 주문의 내 잔액 변동"),
        transactions.length === 0
            ? h("p", { class: "text-muted" }, "아직 내 잔액에 변동이 없습니다.")
            : h("div", { class: "rows" }, transactions.map((t) => h("div", { class: "list-row" },
                h("div", null,
                    badge(label("transactionType", t.type), tone("transactionType", t.type)),
                    h("div", { class: "list-row__sub" }, formatDateTime(t.createdAt))),
                h("div", { class: "list-row__side" },
                    h("div", { class: ["list-row__amount", t.amount > 0 ? "text-plus" : "text-minus"] },
                        (t.amount > 0 ? "+" : "−") + formatWon(Math.abs(t.amount))),
                    h("div", { class: "list-row__sub" }, "잔액 " + formatWon(t.balanceAfter)))))));
}

/* ------------------------------------------------------------------ 지금 할 수 있는 일 */

async function send(path, body, rerender, successMessage) {
    const updated = await api("/orders/" + path, { method: "POST", body });
    await refreshBalance().catch(() => null);
    toast(successMessage, "success");
    rerender(updated);
}

/*
 * 상대방의 처리나 자동 처리로 주문 상태가 바뀌어 서버가 409 로 거절하면, 거절 사유를 알리고 주문을 다시 읽어
 * 지금 상태에 맞는 안내와 버튼으로 다시 그린다. 409 가 아니면 false 를 돌려 호출한 쪽이 오류를 보여 주게 한다.
 */
async function refreshAfterConflict(error, order, rerender) {
    if (!(error instanceof ApiError) || error.status !== 409) {
        return false;
    }
    try {
        rerender(await api("/orders/" + order.orderId));
        toast(error.message + " 최신 주문 상태를 다시 불러왔습니다.", "error");
        await refreshBalance().catch(() => null);
    } catch (reloadError) {
        if (!(reloadError instanceof ApiError && reloadError.status === 401)) {
            toast(error.message + " 새로고침해 최신 상태를 확인하세요.", "error");
        }
    }
    return true;
}

function actionPanel(order, rerender) {
    const isBuyer = order.myRole === "BUYER";
    const panel = h("section", { class: "panel panel--accent stack-md" }, h("h2", { class: "panel__title" }, "진행 안내"));
    const add = (...nodes) => nodes.forEach((node) => node && panel.appendChild(node));

    if (order.tradeStatus === "COMPLETED") {
        add(h("p", { class: "notice notice--success" }, label("completionCause", order.completionCause)),
            h("p", { class: "text-muted text-small" }, isBuyer ? "거래가 끝났습니다. 판매자에게 판매대금이 지급됐습니다." : "판매대금이 내 잔액에 지급됐습니다."));
        return panel;
    }
    if (order.tradeStatus === "REFUNDED") {
        add(h("p", { class: "notice notice--warn" }, "환불이 완료됐습니다. 결제 금액 전액이 구매자에게 돌아갔습니다."),
            h("p", { class: "text-muted text-small" }, "환불 처리: " + label("refundDecision", order.refundRequest && order.refundRequest.decision)));
        return panel;
    }
    if (order.tradeStatus === "CANCELLED") {
        add(h("p", { class: "notice" }, "주문이 취소됐습니다. 결제 금액 전액이 구매자에게 돌아갔습니다."));
        return panel;
    }

    if (order.tradeStatus === "ON_HOLD") {
        const request = order.refundRequest;
        const expired = request && isPast(request.responseDeadlineAt);
        if (expired) {
            add(h("p", { class: "notice notice--warn" }, "판매자 응답 기한이 지났습니다. 자동 환불 처리를 기다리는 중입니다."));
            return panel;
        }
        if (isBuyer) {
            add(h("p", { class: "notice notice--info" }, "환불 요청을 접수했습니다. 판매자가 응답하기를 기다리고 있습니다."),
                h("p", { class: "text-small" }, "응답 기한 ", request ? deadline(request.responseDeadlineAt) : "-"),
                h("p", { class: "text-muted text-small" }, "기한까지 판매자가 응답하지 않으면 자동으로 승인되어 결제 금액 전액이 돌아옵니다."));
            return panel;
        }
        add(h("p", { class: "notice notice--warn" }, "구매자가 환불을 요청했습니다. 응답 기한 안에 승인하거나 거절하세요."),
            h("p", { class: "text-small" }, "응답 기한 ", request ? deadline(request.responseDeadlineAt) : "-"),
            h("p", { class: "text-muted text-small" }, "승인하면 반품 없이 결제 금액 전액이 구매자에게 돌아갑니다. 거절하면 거래가 정상 완료되고 판매대금이 지급됩니다. 기한까지 응답하지 않으면 자동으로 승인됩니다."),
            refundDecisionButtons(order, rerender));
        return panel;
    }

    // 거래 진행 중
    if (order.shippingStatus === "WAITING_SHIPMENT") {
        const expired = isPast(order.shipDeadlineAt);
        if (expired) {
            add(h("p", { class: "notice notice--warn" }, "발송 기한이 지났습니다. 자동 취소 처리를 기다리는 중입니다. 자동 취소되면 결제 금액 전액이 구매자에게 돌아갑니다."));
        } else if (isBuyer) {
            add(h("p", { class: "notice notice--info" }, "판매자의 발송을 기다리고 있습니다."),
                h("p", { class: "text-small" }, "발송 기한 ", deadline(order.shipDeadlineAt)),
                h("p", { class: "text-muted text-small" }, "기한까지 발송되지 않으면 주문이 자동으로 취소되고 결제 금액 전액이 돌아옵니다."));
        } else {
            add(h("p", { class: "notice notice--info" }, "발송 기한 안에 상품을 보내고 발송 정보를 등록하세요."),
                h("p", { class: "text-small" }, "발송 기한 ", deadline(order.shipDeadlineAt)),
                shipmentForm(order, rerender));
        }
        add(cancelFold(order, rerender));
        return panel;
    }

    if (order.shippingStatus === "SHIPPING") {
        add(h("p", { class: "notice notice--info" }, "배송 중입니다."),
            order.shipment ? h("p", { class: "text-small" }, "배송 완료 예정 ", h("span", { class: "deadline" }, formatDateTime(order.shipment.deliveryDueAt))) : null,
            h("p", { class: "text-muted text-small" }, "모의 배송이라 예정 시각이 지나면 시스템이 배송 완료로 바꿉니다. 발송 뒤에는 주문을 취소할 수 없습니다."));
        return panel;
    }

    // 배송 완료
    if (isPast(order.inspectionDeadlineAt)) {
        add(h("p", { class: "notice notice--warn" }, "상품 확인 기간이 끝났습니다. 자동 거래 완료 처리를 기다리는 중입니다."));
        return panel;
    }
    if (!isBuyer) {
        add(h("p", { class: "notice notice--info" }, "배송이 완료됐습니다. 구매자의 확인을 기다리고 있습니다."),
            h("p", { class: "text-small" }, "확인 기한 ", deadline(order.inspectionDeadlineAt)),
            h("p", { class: "text-muted text-small" }, "기한까지 구매자가 구매를 확정하거나 환불을 요청하지 않으면 거래가 자동으로 완료되고 판매대금이 지급됩니다."));
        return panel;
    }
    add(h("p", { class: "notice notice--info" }, "상품을 받으셨나요? 상태를 확인한 뒤 구매를 확정하세요."),
        h("p", { class: "text-small" }, "확인 기한 ", deadline(order.inspectionDeadlineAt)),
        h("p", { class: "text-muted text-small" }, "구매를 확정하면 판매자에게 판매대금이 지급되고 더 이상 환불을 요청할 수 없습니다. 기한까지 아무 요청이 없으면 거래가 자동으로 완료됩니다."),
        confirmButton(order, rerender),
        refundFold(order, rerender));
    return panel;
}

function shipmentForm(order, rerender) {
    const requestId = requestIdHolder();
    const carrier = h("input", { class: "input", type: "text", maxLength: 100, placeholder: "예) CJ대한통운" });
    const tracking = h("input", { class: "input", type: "text", maxLength: 100, placeholder: "운송장 번호" });
    const submit = h("button", { class: "btn btn--primary btn--block", type: "submit" }, "발송 정보 등록");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        field("택배사", carrier, { name: "carrierName" }),
        field("운송장 번호", tracking, { name: "trackingNumber" }),
        h("p", { class: "field__hint" }, "등록한 발송 정보는 고칠 수 없습니다."),
        submit);
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            try {
                await send(order.orderId + "/shipment",
                    { carrierName: carrier.value.trim(), trackingNumber: tracking.value.trim(), requestId: requestId.value },
                    rerender, "발송 정보를 등록했습니다.");
                requestId.settle(null);
            } catch (error) {
                requestId.settle(error);
                if (!(await refreshAfterConflict(error, order, rerender))) {
                    showErrors(form, error);
                }
            }
        });
    });
    return form;
}

function cancelFold(order, rerender) {
    const reason = h("textarea", { class: "input", rows: 3, maxLength: 500, placeholder: "취소 사유" });
    const submit = h("button", { class: "btn btn--danger btn--block", type: "submit" }, "주문 취소하기");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        field("취소 사유", reason, { name: "reason" }),
        h("p", { class: "field__hint" }, "발송 전에만 취소할 수 있으며, 취소하면 결제 금액 전액이 구매자에게 돌아갑니다. 판매된 수량은 다시 판매 수량으로 돌아가지 않습니다."),
        submit);
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            const ok = await confirmDialog({ title: "주문을 취소할까요?", message: "취소한 주문은 되돌릴 수 없습니다.", confirmLabel: "주문 취소", cancelLabel: "닫기", danger: true });
            if (!ok) {
                return;
            }
            try {
                await send(order.orderId + "/cancel", { reason: reason.value.trim() }, rerender, "주문을 취소했습니다.");
            } catch (error) {
                if (!(await refreshAfterConflict(error, order, rerender))) {
                    showErrors(form, error);
                }
            }
        });
    });
    return h("details", { class: "fold" }, h("summary", null, "주문 취소"), h("div", { class: "fold__body" }, form));
}

function confirmButton(order, rerender) {
    const button = h("button", { class: "btn btn--primary btn--block btn--lg", type: "button" }, "구매 확정");
    button.addEventListener("click", () => withBusy(button, async () => {
        const ok = await confirmDialog({
            title: "구매를 확정할까요?",
            message: "확정하면 판매자에게 판매대금이 지급되고 환불을 요청할 수 없습니다.",
            confirmLabel: "구매 확정"
        });
        if (!ok) {
            return;
        }
        try {
            await send(order.orderId + "/confirm-receipt", undefined, rerender, "구매를 확정했습니다.");
        } catch (error) {
            if (!(await refreshAfterConflict(error, order, rerender))) {
                toastError(error);
            }
        }
    }));
    return button;
}

function refundFold(order, rerender) {
    const requestId = requestIdHolder();
    const detail = h("textarea", { class: "input", rows: 4, maxLength: 2000, placeholder: "상품 설명과 어떻게 다른지 적어 주세요." });
    const submit = h("button", { class: "btn btn--danger btn--block", type: "submit" }, "환불 요청하기");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        h("div", { class: "field" }, h("label", null, "환불 사유"), h("p", { class: "notice" }, label("refundReason", "DESCRIPTION_MISMATCH"))),
        field("상세 설명", detail, { name: "detail" }),
        h("p", { class: "field__hint" }, "환불 요청은 한 번만 할 수 있고 고치거나 철회할 수 없습니다. 판매자가 승인하거나 응답 기한까지 응답하지 않으면 반품 없이 전액 환불됩니다. 판매자가 거절하면 거래가 완료됩니다."),
        submit);
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            const ok = await confirmDialog({ title: "환불을 요청할까요?", message: "요청한 뒤에는 수정하거나 철회할 수 없습니다.", confirmLabel: "환불 요청", danger: true });
            if (!ok) {
                return;
            }
            try {
                await send(order.orderId + "/refund-request",
                    { reasonCode: "DESCRIPTION_MISMATCH", detail: detail.value.trim(), requestId: requestId.value },
                    rerender, "환불 요청을 접수했습니다.");
                requestId.settle(null);
            } catch (error) {
                requestId.settle(error);
                if (!(await refreshAfterConflict(error, order, rerender))) {
                    showErrors(form, error);
                }
            }
        });
    });
    return h("details", { class: "fold" }, h("summary", null, "상품이 설명과 달라요 (환불 요청)"), h("div", { class: "fold__body" }, form));
}

function refundDecisionButtons(order, rerender) {
    const approve = h("button", { class: "btn btn--primary grow", type: "button" }, "환불 승인");
    const reject = h("button", { class: "btn btn--ghost grow", type: "button" }, "환불 거절");
    const respond = (button, action) => withBusy(button, async () => {
        const approving = action === "approve";
        const ok = await confirmDialog({
            title: approving ? "환불을 승인할까요?" : "환불을 거절할까요?",
            message: approving
                ? "반품 없이 결제 금액 " + formatWon(order.paidAmount) + " 전액이 구매자에게 돌아갑니다."
                : "거래가 정상 완료되고 판매대금 " + formatWon(order.paidAmount) + "이 내 잔액에 지급됩니다. 재심이나 이의 제기 절차는 없습니다.",
            confirmLabel: approving ? "승인" : "거절",
            danger: approving
        });
        if (!ok) {
            return;
        }
        try {
            await send(order.orderId + "/refund-request/" + action, undefined, rerender, approving ? "환불을 승인했습니다." : "환불을 거절했습니다.");
        } catch (error) {
            if (!(await refreshAfterConflict(error, order, rerender))) {
                toastError(error);
            }
        }
    });
    approve.addEventListener("click", () => respond(approve, "approve"));
    reject.addEventListener("click", () => respond(reject, "reject"));
    return h("div", { class: "actions" }, approve, reject);
}
