/*
 * 화면 요소를 만드는 도구. 사용자 입력과 서버 값은 모두 텍스트 노드로 넣고 innerHTML 은 쓰지 않는다.
 */
import { ApiError } from "./api.js";

/*
 * h("a", { class: "x", href: "#/", onClick: fn, dataset: {...}, attrs: {...} }, "글자", 자식요소, [배열])
 * 문자열·숫자 자식은 텍스트 노드가 되고 null·false·undefined 는 건너뛴다.
 */
export function h(tag, props, ...children) {
    const element = document.createElement(tag);
    if (props) {
        Object.entries(props).forEach(([key, value]) => {
            if (value === null || value === undefined || value === false) {
                return;
            }
            if (key === "class") {
                element.className = Array.isArray(value) ? value.filter(Boolean).join(" ") : value;
            } else if (key === "dataset") {
                Object.assign(element.dataset, value);
            } else if (key === "attrs") {
                Object.entries(value).forEach(([name, attr]) => {
                    if (attr !== null && attr !== undefined && attr !== false) {
                        element.setAttribute(name, attr === true ? "" : String(attr));
                    }
                });
            } else if (key.startsWith("on") && typeof value === "function") {
                element.addEventListener(key.slice(2).toLowerCase(), value);
            } else if (key in element) {
                element[key] = value;
            } else {
                element.setAttribute(key, value === true ? "" : String(value));
            }
        });
    }
    append(element, children);
    return element;
}

function append(parent, children) {
    children.forEach((child) => {
        if (child === null || child === undefined || child === false) {
            return;
        }
        if (Array.isArray(child)) {
            append(parent, child);
        } else if (child instanceof Node) {
            parent.appendChild(child);
        } else {
            parent.appendChild(document.createTextNode(String(child)));
        }
    });
}

export function badge(text, toneName) {
    return h("span", { class: ["badge", toneName ? "badge--" + toneName : null] }, text);
}

export function loading(text = "불러오는 중입니다") {
    return h("div", { class: "loading" }, text);
}

export function emptyState(title, message, action) {
    return h("div", { class: "empty" },
        h("div", { class: "empty__title" }, title),
        message ? h("p", null, message) : null,
        action || null);
}

export function errorState(error, retry) {
    const message = error instanceof Error ? error.message : String(error);
    return emptyState("화면을 불러오지 못했습니다", message,
        retry ? h("button", { class: "btn btn--ghost", type: "button", onClick: retry }, "다시 시도") : null);
}

/* ---- 알림 ---- */
let toastStack = null;

export function toast(message, toneName = "info") {
    if (!toastStack) {
        toastStack = h("div", { class: "toast-stack", attrs: { role: "status", "aria-live": "polite" } });
        document.body.appendChild(toastStack);
    }
    const item = h("div", { class: ["toast", toneName !== "info" ? "toast--" + toneName : null] }, message);
    toastStack.appendChild(item);
    window.setTimeout(() => item.remove(), toneName === "error" ? 6000 : 3500);
}

export function toastError(error) {
    if (error instanceof ApiError && error.status === 401) {
        return;
    }
    toast(error instanceof Error ? error.message : String(error), "error");
}

let elementSequence = 0;

export function nextId(prefix) {
    elementSequence += 1;
    return prefix + "-" + elementSequence;
}

/* ---- 확인 창 ---- */

/*
 * 제목과 안내 문구를 화면 읽기 프로그램에 연결한다.
 * 이 화면의 확인 창은 모두 되돌릴 수 없는 요청(결제, 등록, 취소, 확정, 환불, 제안 응답)을 묻으므로 처음 초점은 항상 취소 버튼에 둔다.
 * danger 는 확인 버튼을 빨간색으로 보여 줄지 여부만 정한다.
 */
export function confirmDialog({ title, message, confirmLabel = "확인", cancelLabel = "취소", danger = false }) {
    return new Promise((resolve) => {
        const titleId = nextId("dialog-title");
        const messageId = nextId("dialog-message");
        const confirmButton = h("button", { class: ["btn", danger ? "btn--danger" : "btn--primary"], type: "button" }, confirmLabel);
        const cancelButton = h("button", { class: "btn btn--ghost", type: "button" }, cancelLabel);
        const dialog = h("dialog", { class: "modal", attrs: { "aria-labelledby": titleId, "aria-describedby": message ? messageId : null } },
            h("div", { class: "modal__body" },
                h("h2", { class: "modal__title", id: titleId }, title),
                message ? h("p", { class: "modal__message", id: messageId }, message) : null),
            h("div", { class: "modal__actions" }, cancelButton, confirmButton));
        let result = false;
        confirmButton.addEventListener("click", () => { result = true; dialog.close(); });
        cancelButton.addEventListener("click", () => dialog.close());
        dialog.addEventListener("close", () => { dialog.remove(); resolve(result); });
        document.body.appendChild(dialog);
        dialog.showModal();
        cancelButton.focus();
    });
}

/* ---- 폼 ---- */

/*
 * 입력 칸 묶음. control 에 data-field 를 붙여 서버의 fieldErrors 와 연결하고,
 * 안내 문구와 오류 문구를 aria-describedby 로 입력 칸에 연결한다.
 */
export function field(labelText, control, { hint, name } = {}) {
    const id = control.id || nextId("field");
    control.id = id;
    if (name) {
        control.dataset.field = name;
    }
    const hintId = hint ? id + "-hint" : null;
    const errorId = id + "-error";
    control.setAttribute("aria-describedby", [hintId, errorId].filter(Boolean).join(" "));
    const error = h("p", { class: "field__error", id: errorId, hidden: true });
    const wrapper = h("div", { class: "field", dataset: name ? { fieldWrap: name } : undefined },
        h("label", { htmlFor: id }, labelText),
        control,
        hint ? h("p", { class: "field__hint", id: hintId }, hint) : null,
        error);
    return wrapper;
}

export function clearErrors(form) {
    form.querySelectorAll(".field__error").forEach((element) => { element.hidden = true; element.textContent = ""; });
    form.querySelectorAll(".is-invalid").forEach((element) => {
        element.classList.remove("is-invalid");
        element.removeAttribute("aria-invalid");
    });
    const formError = form.querySelector(".form__error");
    if (formError) {
        formError.hidden = true;
        formError.textContent = "";
    }
}

/*
 * 필드 오류는 해당 칸 아래에, 나머지는 폼 위쪽 오류 영역(role=alert)에 보여 준다.
 * 모든 오류를 칸 아래에 놓은 경우에도 위쪽 영역에 요약을 넣어 실패를 알리고, 첫 오류 칸으로 초점을 옮긴다.
 */
export function showErrors(form, error) {
    clearErrors(form);
    const fieldErrors = (error instanceof ApiError && error.fieldErrors) || {};
    let placed = 0;
    Object.entries(fieldErrors).forEach(([name, message]) => {
        const wrap = form.querySelector(`[data-field-wrap="${CSS.escape(name)}"]`);
        if (!wrap) {
            return;
        }
        const control = wrap.querySelector("[data-field]");
        const target = wrap.querySelector(".field__error");
        if (control) {
            control.classList.add("is-invalid");
            control.setAttribute("aria-invalid", "true");
        }
        target.textContent = message;
        target.hidden = false;
        placed++;
    });
    const formError = form.querySelector(".form__error");
    if (formError) {
        const allPlaced = placed > 0 && placed === Object.keys(fieldErrors).length;
        formError.textContent = allPlaced
            ? "표시된 입력 칸을 확인하세요."
            : (error instanceof Error ? error.message : String(error));
        formError.hidden = false;
    }
    // 서버의 fieldErrors 순서는 화면 순서와 다르므로 화면에서 처음 나오는 오류 칸을 찾는다.
    const firstInvalid = form.querySelector('[data-field][aria-invalid="true"]');
    if (firstInvalid) {
        firstInvalid.focus();
    }
}

export function formError() {
    return h("p", { class: "form__error", hidden: true, attrs: { role: "alert" } });
}

/* 요청 중에는 버튼을 잠가 같은 버튼을 연달아 누르지 않게 한다(화면 동작이며 서버의 중복 판단과는 별개다). */
export async function withBusy(button, task) {
    if (button.disabled) {
        return undefined;
    }
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "처리 중...";
    try {
        return await task();
    } finally {
        button.disabled = false;
        button.textContent = original;
    }
}

/* 숫자 입력값. 빈 칸은 null, 그 밖에는 Number 로 바꿔 보낸다. 정수·범위 판단은 서버가 한다. */
export function numberValue(input) {
    const raw = input.value.trim();
    if (raw === "") {
        return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : raw;
}
