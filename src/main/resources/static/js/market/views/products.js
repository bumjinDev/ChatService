/*
 * 상품 목록(F-002), 상품 상세와 대화 시작(F-005)·구매(F-010) 화면.
 * 버튼 노출은 화면 편의를 위한 판단이며 실제 허용 여부는 서버가 정한다.
 */
import { api, ApiError, requestIdHolder } from "../api.js";
import { h, badge, loading, emptyState, errorState, toast, toastError, confirmDialog, field, formError, clearErrors, showErrors, withBusy, numberValue } from "../dom.js";
import { CATEGORIES, formatWon, formatNumber, formatDateTime, label, tone, multiplyAmount } from "../format.js";
import { session, links, refreshBalance } from "../shell.js";

export async function productListView(mount, ctx) {
    const category = ctx.query.get("category");
    const validCategory = CATEGORIES.some(([value]) => value === category) ? category : null;

    const filters = h("div", { class: "filters", attrs: { role: "tablist", "aria-label": "카테고리" } },
        h("a", { class: ["filter-chip", validCategory ? null : "is-active"], href: "#/products" }, "전체"),
        CATEGORIES.map(([value, text]) =>
            h("a", { class: ["filter-chip", validCategory === value ? "is-active" : null], href: "#/products?category=" + value }, text)));

    const body = h("div", null, loading());
    mount.replaceChildren(
        h("div", { class: "page-head" },
            h("div", null,
                h("h1", { class: "page-head__title" }, "판매 중인 상품"),
                h("p", { class: "page-head__sub" }, "테스트 잔액으로 사고파는 개인 간 거래입니다. 판매 중인 상품만 보입니다.")),
            h("div", { class: "page-head__actions" },
                h("a", { class: "btn btn--primary", href: "#/sell" }, "상품 판매하기"))),
        filters,
        body);

    const load = async () => {
        body.replaceChildren(loading());
        try {
            const products = await api("/products" + (validCategory ? "?category=" + encodeURIComponent(validCategory) : ""));
            if (products.length === 0) {
                body.replaceChildren(emptyState("판매 중인 상품이 없습니다",
                    validCategory ? "다른 카테고리를 선택하거나 직접 상품을 등록해 보세요." : "첫 상품을 등록해 보세요.",
                    h("a", { class: "btn btn--primary", href: "#/sell" }, "상품 판매하기")));
                return;
            }
            body.replaceChildren(h("div", { class: "product-grid" }, products.map(productCard)));
        } catch (error) {
            body.replaceChildren(errorState(error, load));
        }
    };
    await load();
}

function productCard(product) {
    return h("a", { class: "product-card", href: "#/products/" + product.productId },
        h("div", { class: "badges" }, badge(label("category", product.category), "accent")),
        h("div", { class: "product-card__name" }, product.name),
        h("div", { class: "product-card__price" }, formatWon(product.price)),
        h("div", { class: "product-card__meta" },
            h("span", null, "남은 수량 " + formatNumber(product.remainingQuantity) + "개"),
            h("span", null, product.sellerNickname)),
        h("div", { class: "text-muted text-small" }, formatDateTime(product.createdAt) + " 등록"));
}

/* 이 상품에 대한 내 대화(구매 희망자로 참여)와 그 대화의 수락된 제안을 찾는다. */
async function findBuyerConversation(productId) {
    const conversations = await api("/conversations");
    const summary = conversations.find((c) => c.product.productId === productId && c.myRole === "BUYER");
    if (!summary) {
        return { conversation: null, acceptedOffer: null };
    }
    const detail = await api("/conversations/" + summary.conversationId);
    const acceptedOffer = (detail.offers || []).find((offer) => offer.status === "ACCEPTED") || null;
    return { conversation: detail, acceptedOffer };
}

export async function productDetailView(mount, ctx) {
    const productId = Number(ctx.params[0]);
    mount.replaceChildren(loading());

    let product;
    try {
        product = await api("/products/" + productId);
    } catch (error) {
        // 판매가 끝난 상품은 공개 상세를 주지 않으므로(F-002) 없는 상품과 같은 404 가 온다.
        if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") {
            mount.replaceChildren(emptyState("상품을 볼 수 없습니다",
                "판매가 끝났거나 없는 상품입니다. 판매가 끝난 상품의 상세는 공개하지 않습니다. 주문한 상품은 구매 내역에서 확인하세요.",
                h("div", { class: "actions" },
                    h("a", { class: "btn btn--ghost", href: "#/products" }, "상품 목록으로"),
                    session.loggedIn ? h("a", { class: "btn btn--ghost", href: "#/purchases" }, "구매 내역") : null)));
            return;
        }
        mount.replaceChildren(errorState(error, () => productDetailView(mount, ctx)));
        return;
    }

    const isMine = session.loggedIn && session.nickname && product.sellerNickname === session.nickname;
    let buyerContext = { conversation: null, acceptedOffer: null };
    if (session.loggedIn && !isMine) {
        try {
            [buyerContext] = await Promise.all([findBuyerConversation(productId), refreshBalance()]);
        } catch (error) {
            toastError(error);
        }
    }

    const info = h("section", { class: "panel" },
        h("div", { class: "badges" },
            badge(label("category", product.category), "accent"),
            badge(label("productStatus", product.status), tone("productStatus", product.status))),
        h("h1", { class: "product-title" }, product.name),
        h("div", { class: "product-price" }, formatWon(product.price), h("span", { class: "text-muted text-small" }, " / 개")),
        h("hr", { class: "divider" }),
        h("dl", { class: "kv" },
            h("dt", null, "판매자"), h("dd", null, product.sellerNickname + (isMine ? " (나)" : "")),
            h("dt", null, "남은 수량"), h("dd", { dataset: { remaining: "" } }, remainingText(product)),
            h("dt", null, "등록일"), h("dd", null, formatDateTime(product.createdAt))),
        h("hr", { class: "divider" }),
        h("h2", { class: "panel__title" }, "상품 설명"),
        h("p", { class: "product-desc" }, product.description));

    const aside = h("aside", null, purchasePanel(product, isMine, buyerContext, ctx));

    mount.replaceChildren(
        h("a", { class: "back-link", href: "#/products" }, "← 상품 목록"),
        h("div", { class: "layout-2col" }, info, aside));
}

function remainingText(product) {
    return formatNumber(product.remainingQuantity) + "개 / 최초 " + formatNumber(product.initialQuantity) + "개";
}

function purchasePanel(product, isMine, buyerContext, ctx) {
    if (!session.loggedIn) {
        return h("section", { class: "panel panel--accent stack-md" },
            h("h2", { class: "panel__title" }, "구매하려면 로그인하세요"),
            h("p", { class: "text-muted" }, "로그인하면 판매자에게 문의하고 가격을 제안하거나 바로 구매할 수 있습니다."),
            h("div", { class: "actions" },
                h("a", { class: "btn btn--primary", href: links.login }, "로그인"),
                h("a", { class: "btn btn--ghost", href: links.join }, "회원가입")));
    }
    if (isMine) {
        return h("section", { class: "panel stack-md" },
            h("h2", { class: "panel__title" }, "내가 등록한 상품입니다"),
            h("p", { class: "text-muted" }, "구매 희망자의 문의와 가격 제안은 대화 화면에서, 들어온 주문은 판매 내역에서 확인합니다. 등록한 상품은 수정하거나 삭제할 수 없습니다."),
            h("div", { class: "actions" },
                h("a", { class: "btn btn--primary", href: links.conversations }, "대화 보기"),
                h("a", { class: "btn btn--ghost", href: links.sales }, "판매 내역")));
    }

    const conversation = buyerContext.conversation;
    const chatButton = h("button", { class: "btn btn--ghost btn--block", type: "button" },
        conversation ? "판매자와의 대화 열기" : "판매자에게 문의하기");
    chatButton.addEventListener("click", () => withBusy(chatButton, async () => {
        try {
            const detail = await api("/products/" + product.productId + "/conversations", { method: "POST" });
            window.location.href = links.conversation(detail.conversationId);
        } catch (error) {
            toastError(error);
        }
    }));

    return h("section", { class: "panel panel--accent stack-md" },
        h("h2", { class: "panel__title" }, "구매하기"),
        orderForm(product, buyerContext.acceptedOffer, ctx),
        h("hr", { class: "divider" }),
        h("p", { class: "text-muted text-small" }, "가격을 흥정하려면 판매자와 대화에서 개당 가격을 제안하세요. 판매자가 수락하면 여기서 합의 가격을 선택할 수 있습니다."),
        chatButton);
}

function orderForm(product, acceptedOffer, ctx) {
    const requestId = requestIdHolder();
    const preferOffer = acceptedOffer && String(acceptedOffer.offerId) === ctx.query.get("offer");

    const listedRadio = h("input", { type: "radio", name: "price", value: "LISTED", checked: !preferOffer });
    const priceChoice = h("div", { class: "choice", dataset: { fieldWrap: "offerId" } },
        h("label", { class: "choice__item" }, listedRadio,
            h("span", { class: "choice__label" }, "등록 가격"),
            h("span", { class: "choice__value" }, formatWon(product.price))));
    let offerRadio = null;
    if (acceptedOffer) {
        offerRadio = h("input", { type: "radio", name: "price", value: "AGREED", checked: !!preferOffer });
        priceChoice.appendChild(h("label", { class: "choice__item" }, offerRadio,
            h("span", { class: "choice__label" }, "합의 가격 (수락된 제안)"),
            h("span", { class: "choice__value" }, formatWon(acceptedOffer.amount))));
    }
    priceChoice.appendChild(h("p", { class: "field__error", hidden: true }));

    const quantity = h("input", { class: "input", type: "number", min: "1", step: "1", value: "1", inputMode: "numeric", max: String(product.remainingQuantity) });
    const minus = h("button", { class: "btn btn--ghost", type: "button", attrs: { "aria-label": "수량 줄이기" } }, "−");
    const plus = h("button", { class: "btn btn--ghost", type: "button", attrs: { "aria-label": "수량 늘리기" } }, "+");
    const stepQuantity = (delta) => {
        const current = Number.parseInt(quantity.value, 10) || 0;
        quantity.value = String(Math.min(Math.max(current + delta, 1), product.remainingQuantity));
        updateSummary();
    };
    minus.addEventListener("click", () => stepQuantity(-1));
    plus.addEventListener("click", () => stepQuantity(1));
    const quantityHint = () => "남은 수량 " + formatNumber(product.remainingQuantity) + "개까지 구매할 수 있습니다.";
    const quantityField = field("구매 수량", quantity, { name: "quantity", hint: quantityHint() });
    const quantityBox = h("div", { class: "qty" }, minus);
    quantity.replaceWith(quantityBox);
    quantityBox.append(quantity, plus);

    const recipient = h("input", { class: "input", type: "text", maxLength: 100, placeholder: "받는 분 이름", autocomplete: "name" });
    const address = h("textarea", { class: "input", maxLength: 500, rows: 3, placeholder: "배송 받을 주소" });

    const unitRow = h("strong", null, "-");
    const totalRow = h("strong", null, "-");
    const balanceRow = h("strong", null, session.balance === null ? "-" : formatWon(session.balance));
    const shortage = h("div", { class: "summary__row summary__row--warn", hidden: true });
    const summary = h("div", { class: "summary" },
        h("div", { class: "summary__row" }, h("span", null, "개당 가격"), unitRow),
        h("div", { class: "summary__row" }, h("span", null, "내 잔액"), balanceRow),
        h("div", { class: "summary__row summary__row--total" }, h("span", null, "결제 금액"), totalRow),
        shortage);

    const unitPrice = () => (offerRadio && offerRadio.checked ? acceptedOffer.amount : product.price);
    function updateSummary() {
        const qty = Number(quantity.value);
        unitRow.textContent = formatWon(unitPrice());
        if (!Number.isSafeInteger(qty) || qty < 1) {
            totalRow.textContent = "-";
            shortage.hidden = true;
            return;
        }
        const total = multiplyAmount(unitPrice(), qty);
        totalRow.textContent = formatWon(total);
        if (session.balance !== null && total > BigInt(session.balance)) {
            shortage.replaceChildren("잔액이 " + formatWon(total - BigInt(session.balance)) + " 부족합니다. ",
                h("a", { href: "#/wallet" }, "충전하기"));
            shortage.hidden = false;
        } else {
            shortage.hidden = true;
        }
    }
    async function refreshStock() {
        try {
            const latest = await api("/products/" + product.productId);
            product.remainingQuantity = latest.remainingQuantity;
            product.initialQuantity = latest.initialQuantity;
            quantity.max = String(latest.remainingQuantity);
            const hint = document.getElementById(quantity.id + "-hint");
            if (hint) {
                hint.textContent = quantityHint();
            }
            const remaining = document.querySelector("[data-remaining]");
            if (remaining) {
                remaining.textContent = remainingText(product);
            }
        } catch (error) {
            if (error instanceof ApiError && error.code === "PRODUCT_NOT_FOUND") {
                ctx.reload();
            }
        }
    }
    quantity.addEventListener("input", updateSummary);
    listedRadio.addEventListener("change", updateSummary);
    if (offerRadio) {
        offerRadio.addEventListener("change", updateSummary);
    }

    const submit = h("button", { class: "btn btn--primary btn--block btn--lg", type: "submit" }, "결제하기");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        h("div", { class: "field" }, h("label", null, "적용 가격"), priceChoice),
        quantityField,
        field("수령인", recipient, { name: "recipientName" }),
        field("배송 주소", address, { name: "shippingAddress" }),
        summary,
        h("p", { class: "notice" }, "결제하면 결제 금액이 잔액에서 바로 빠집니다. 판매자가 발송 기한(결제 후 5영업일)까지 발송하지 않으면 주문이 자동으로 취소되고 결제 금액 전액이 돌아옵니다."),
        submit);
    updateSummary();

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            const body = {
                productId: product.productId,
                quantity: numberValue(quantity),
                recipientName: recipient.value.trim(),
                shippingAddress: address.value.trim(),
                offerId: offerRadio && offerRadio.checked ? acceptedOffer.offerId : null,
                requestId: requestId.value
            };
            const qty = Number(body.quantity);
            const totalText = Number.isSafeInteger(qty) && qty > 0 ? formatWon(multiplyAmount(unitPrice(), qty)) : "입력한 수량의 금액";
            const ok = await confirmDialog({
                title: "결제할까요?",
                message: `${product.name}\n${formatNumber(Number.isSafeInteger(qty) ? qty : 0)}개 × ${formatWon(unitPrice())}\n결제 금액 ${totalText}`,
                confirmLabel: "결제하기"
            });
            if (!ok) {
                return;
            }
            try {
                const order = await api("/orders", { method: "POST", body });
                requestId.settle(null);
                await refreshBalance().catch(() => null);
                toast("결제가 완료되었습니다.", "success");
                ctx.navigate("#/orders/" + order.orderId);
            } catch (error) {
                requestId.settle(error);
                if (error instanceof ApiError && error.outcomeUnknown) {
                    // F-019: 결제 응답을 받지 못하면 결과를 구매 내역에서 확인하도록 안내한다.
                    showErrors(form, new Error("결제 결과를 확인하지 못했습니다. 다시 결제하기 전에 구매 내역에서 주문이 만들어졌는지 확인하세요."));
                    return;
                }
                showErrors(form, error);
                if (error instanceof ApiError && error.code === "INSUFFICIENT_STOCK") {
                    // 입력한 수령인·주소·가격 선택은 그대로 두고 남은 수량만 새로 반영한다.
                    await refreshStock();
                } else if (error instanceof ApiError && ["PRODUCT_NOT_ON_SALE", "INVALID_OFFER_SELECTION"].includes(error.code)) {
                    toast(error.message + " 최신 상품 정보를 다시 불러옵니다.", "error");
                    window.setTimeout(() => ctx.reload(), 1200);
                }
            }
        });
    });
    return form;
}
