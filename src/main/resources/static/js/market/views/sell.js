/* 상품 등록 화면(F-001). */
import { api, ApiError } from "../api.js";
import { h, toast, confirmDialog, field, formError, clearErrors, showErrors, withBusy, numberValue } from "../dom.js";
import { CATEGORIES } from "../format.js";

export async function sellView(mount, ctx) {
    const name = h("input", { class: "input", type: "text", maxLength: 100, placeholder: "예) 아이폰 15 128GB 블루" });
    const category = h("select", { class: "input" },
        h("option", { value: "" }, "카테고리를 선택하세요"),
        CATEGORIES.map(([value, text]) => h("option", { value }, text)));
    const price = h("input", { class: "input", type: "number", min: "1", step: "1", inputMode: "numeric", placeholder: "개당 가격(원)" });
    const quantity = h("input", { class: "input", type: "number", min: "1", step: "1", inputMode: "numeric", placeholder: "판매 수량", value: "1" });
    const description = h("textarea", { class: "input", rows: 8, placeholder: "상품 상태, 구성품, 사용 기간 등을 적어 주세요. 환불 요청은 설명과 실제 상태가 다를 때 할 수 있습니다." });

    const submit = h("button", { class: "btn btn--primary btn--lg", type: "submit" }, "상품 등록하기");
    const form = h("form", { class: "form", attrs: { novalidate: true } },
        formError(),
        field("상품명", name, { name: "name", hint: "100자까지 입력할 수 있습니다." }),
        h("div", { class: "form-row" },
            field("카테고리", category, { name: "category" }),
            field("판매 수량", quantity, { name: "quantity", hint: "같은 상태·가격의 물품 개수입니다." })),
        field("개당 가격", price, { name: "price", hint: "1원 이상의 정수로 입력합니다." }),
        field("상품 설명", description, { name: "description" }),
        h("p", { class: "notice notice--warn" }, "등록한 상품은 수정·삭제·판매 중단을 할 수 없습니다. 내용을 확인한 뒤 등록하세요."),
        h("div", { class: "actions" }, submit, h("a", { class: "btn btn--ghost", href: "#/products" }, "취소")));

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        withBusy(submit, async () => {
            clearErrors(form);
            const ok = await confirmDialog({
                title: "상품을 등록할까요?",
                message: "등록한 뒤에는 상품 정보를 바꾸거나 판매를 중단할 수 없습니다.",
                confirmLabel: "등록하기"
            });
            if (!ok) {
                return;
            }
            try {
                const product = await api("/products", {
                    method: "POST",
                    body: {
                        name: name.value.trim(),
                        description: description.value,
                        category: category.value || null,
                        price: numberValue(price),
                        quantity: numberValue(quantity)
                    }
                });
                toast("상품을 등록했습니다.", "success");
                ctx.navigate("#/products/" + product.productId);
            } catch (error) {
                showErrors(form, error);
                if (!(error instanceof ApiError) || error.status !== 400) {
                    toast(error.message, "error");
                }
            }
        });
    });

    mount.replaceChildren(h("div", { class: "narrow" },
        h("div", { class: "page-head" },
            h("div", null,
                h("h1", { class: "page-head__title" }, "상품 판매하기"),
                h("p", { class: "page-head__sub" }, "같은 설명·카테고리·가격으로 파는 물품을 한 번에 등록합니다."))),
        h("section", { class: "panel" }, form)));
}
