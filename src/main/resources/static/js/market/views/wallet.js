/* 테스트 잔액 충전(F-003)과 잔액·변동 내역 조회(F-004). */
import { api, requestIdHolder } from "../api.js";
import { h, badge, loading, emptyState, errorState, toast, field, formError, clearErrors, showErrors, withBusy, numberValue } from "../dom.js";
import { formatWon, formatDateTime, label, tone } from "../format.js";
import { refreshBalance, updateBalance } from "../shell.js";

const QUICK_AMOUNTS = [10000, 50000, 100000, 1000000];

export async function walletView(mount) {
    mount.replaceChildren(loading());
    let wallet;
    let transactions;
    try {
        // 내역 조회도 지갑 행이 없으면 행을 만들므로 잔액 조회가 끝난 뒤에 보낸다.
        wallet = await refreshBalance();
        transactions = await api("/wallet/transactions");
    } catch (error) {
        mount.replaceChildren(errorState(error, () => walletView(mount)));
        return;
    }
    const balanceText = h("div", { class: "wallet-hero__value" }, formatWon(wallet.balance));
    const ledger = h("div", { class: "rows" });

    const renderLedger = () => {
        if (transactions.length === 0) {
            ledger.replaceChildren(emptyState("잔액 변동 내역이 없습니다", "충전·결제·환불·판매대금 지급이 생기면 여기에 쌓입니다."));
            return;
        }
        ledger.replaceChildren(...transactions.map(transactionRow));
    };
    renderLedger();

    const requestId = requestIdHolder();
    const amount = h("input", { class: "input", type: "number", min: "1", step: "1", inputMode: "numeric", placeholder: "충전할 금액(원)" });
    const quick = h("div", { class: "quick-amounts" },
        QUICK_AMOUNTS.map((value) => h("button", {
            class: "btn btn--soft btn--sm", type: "button",
            onClick: () => {
                const current = Number(amount.value) || 0;
                amount.value = String(current + value);
                amount.focus();
            }
        }, "+" + formatWon(value))));
    const submit = h("button", { class: "btn btn--primary btn--block", type: "submit" }, "충전하기");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        field("충전 금액", amount, { name: "amount" }),
        quick,
        submit);

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            try {
                const result = await api("/wallet/charges", {
                    method: "POST",
                    body: { amount: numberValue(amount), requestId: requestId.value }
                });
                requestId.settle(null);
                balanceText.textContent = formatWon(result.balance);
                updateBalance(result.balance);
                transactions = [result.transaction, ...transactions];
                renderLedger();
                amount.value = "";
                toast(formatWon(result.transaction.amount) + "을 충전했습니다.", "success");
            } catch (error) {
                requestId.settle(error);
                showErrors(form, error.outcomeUnknown
                    ? new Error("충전 결과를 확인하지 못했습니다. 다시 충전하기 전에 아래 내역을 새로 불러와 확인하세요.")
                    : error);
            }
        });
    });

    mount.replaceChildren(
        h("div", { class: "page-head" },
            h("div", null,
                h("h1", { class: "page-head__title" }, "지갑"),
                h("p", { class: "page-head__sub" }, "구매 결제, 취소·환불 반환, 판매대금 지급이 모두 이 잔액으로 처리됩니다.")),
            h("div", { class: "page-head__actions" },
                h("button", { class: "btn btn--ghost", type: "button", onClick: () => walletView(mount) }, "새로고침"))),
        h("div", { class: "layout-2col" },
            h("section", { class: "panel" },
                h("h2", { class: "panel__title" }, "잔액 변동 내역", h("small", null, "최근 순")),
                ledger),
            h("aside", { class: "stack-md" },
                h("div", { class: "wallet-hero" },
                    h("div", { class: "wallet-hero__label" }, "현재 잔액"),
                    balanceText,
                    h("div", { class: "wallet-hero__note" }, "테스트 잔액입니다. 실제 결제·출금은 없습니다.")),
                h("section", { class: "panel" },
                    h("h2", { class: "panel__title" }, "테스트 잔액 충전"),
                    form))));
}

function transactionRow(transaction) {
    const positive = transaction.amount > 0;
    return h("div", { class: "list-row" },
        h("div", { class: "stack-sm" },
            h("div", { class: "row" },
                badge(label("transactionType", transaction.type), tone("transactionType", transaction.type)),
                transaction.orderId
                    ? h("a", { class: "text-small", href: "#/orders/" + transaction.orderId }, "주문 #" + transaction.orderId)
                    : null),
            h("div", { class: "list-row__sub" }, formatDateTime(transaction.createdAt))),
        h("div", { class: "list-row__side" },
            h("div", { class: ["list-row__amount", positive ? "text-plus" : "text-minus"] },
                (positive ? "+" : "−") + formatWon(Math.abs(transaction.amount))),
            h("div", { class: "list-row__sub" }, "잔액 " + formatWon(transaction.balanceAfter))));
}
