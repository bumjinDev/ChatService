/*
 * REST API 호출 모듈. 모든 요청은 같은 출처의 쿠키(JWT)로 인증한다.
 * 실패 응답은 서버의 {code, status, message, fieldErrors} 를 ApiError 로 바꿔 던진다.
 * 401 을 받으면 로그인 화면으로 이동한다.
 */
export const CONTEXT_PATH = "/ChatService";
const API_BASE = CONTEXT_PATH + "/api";
export const LOGIN_URL = CONTEXT_PATH + "/members/login";

export class ApiError extends Error {
    constructor(status, body) {
        super((body && body.message) || "요청을 처리하지 못했습니다.");
        this.status = status;
        this.code = (body && body.code) || null;
        this.fieldErrors = (body && body.fieldErrors) || {};
    }

    /* 연결 실패나 서버 오류처럼 요청이 처리됐는지 화면에서 알 수 없는 경우 */
    get outcomeUnknown() {
        return this.status === 0 || this.status >= 500;
    }
}

let redirectingToLogin = false;

function goToLogin() {
    if (redirectingToLogin) {
        return;
    }
    redirectingToLogin = true;
    window.location.href = LOGIN_URL;
}

export async function api(path, { method = "GET", body } = {}) {
    const options = { method, credentials: "same-origin", headers: { Accept: "application/json" } };
    if (body !== undefined) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(API_BASE + path, options);
    } catch (e) {
        throw new ApiError(0, { message: "서버에 연결하지 못했습니다. 네트워크 상태를 확인하세요." });
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (e) {
            data = null;
        }
    }

    if (response.status === 401) {
        goToLogin();
        throw new ApiError(401, { code: "UNAUTHENTICATED", message: "로그인이 필요합니다." });
    }
    if (!response.ok) {
        const hasServerMessage = data && typeof data.message === "string" && data.code;
        throw new ApiError(response.status, hasServerMessage ? data : {
            message: response.status >= 500
                ? "서버에서 요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요."
                : "요청을 처리하지 못했습니다."
        });
    }
    return data;
}

/*
 * 요청 식별자(requestId). 서버의 기본 구현은 이 값으로 중복을 판단하지 않고 저장만 한다.
 * crypto.randomUUID 는 HTTPS·localhost 에서만 있으므로 없으면 getRandomValues 로 같은 형식을 만든다.
 */
export function newRequestId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
        return window.crypto.randomUUID();
    }
    const bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/*
 * 폼 하나에서 쓰는 requestId. 성공하거나 서버가 거절(4xx)하면 새 값으로 바꾸고,
 * 결과를 알 수 없는 실패(연결 실패·5xx)에서는 같은 값을 유지해 다시 보낼 때 같은 요청임을 남긴다.
 */
export function requestIdHolder() {
    let current = newRequestId();
    return {
        get value() {
            return current;
        },
        settle(error) {
            if (!error || !(error instanceof ApiError) || !error.outcomeUnknown) {
                current = newRequestId();
            }
        }
    };
}
