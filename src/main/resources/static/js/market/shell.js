/*
 * 두 화면(메인, 대화)이 함께 쓰는 머리글과 로그인 정보.
 * 메인 화면은 서버가 body 의 data-logged-in 에 로그인 여부를, data-user-name 에 닉네임을 넣는다.
 * 대화 화면은 서버가 로그인 여부를 넣지 않으므로 data-auth="required" 로 표시하고 API 401 로 판단한다.
 */
import { api, CONTEXT_PATH, LOGIN_URL } from "./api.js";
import { h } from "./dom.js";
import { formatWon } from "./format.js";

const APP = CONTEXT_PATH + "/";

export const links = {
    products: APP + "#/products",
    product: (id) => APP + "#/products/" + id,
    sell: APP + "#/sell",
    wallet: APP + "#/wallet",
    purchases: APP + "#/purchases",
    sales: APP + "#/sales",
    order: (id) => APP + "#/orders/" + id,
    conversations: CONTEXT_PATH + "/conversations",
    conversation: (id) => CONTEXT_PATH + "/conversations?conversationId=" + id,
    login: LOGIN_URL,
    join: CONTEXT_PATH + "/members/join",
    logout: CONTEXT_PATH + "/logout"
};

function readSession() {
    const body = document.body.dataset;
    if (body.auth === "required") {
        return { loggedIn: true, nickname: null };
    }
    const loggedIn = body.loggedIn === "true";
    return { loggedIn, nickname: loggedIn && body.userName ? body.userName : null };
}

export const session = {
    ...readSession(),
    memberId: null,
    balance: null
};

const NAV = [
    ["products", "상품 둘러보기", links.products],
    ["sell", "판매하기", links.sell],
    ["conversations", "대화", links.conversations],
    ["purchases", "구매 내역", links.purchases],
    ["sales", "판매 내역", links.sales],
    ["wallet", "지갑", links.wallet]
];

let navLinks = {};
let balanceValue = null;
let nameSlot = null;

export function renderHeader(container) {
    navLinks = {};
    const nav = h("nav", { class: "app-nav", attrs: { "aria-label": "주요 메뉴" } },
        NAV.map(([key, text, href]) => {
            const link = h("a", { class: "app-nav__link", href }, text);
            navLinks[key] = link;
            return link;
        }));

    let user;
    if (session.loggedIn) {
        balanceValue = h("span", null, "-");
        nameSlot = h("span", { class: "app-user__name" }, session.nickname ? session.nickname + " 님" : "");
        user = h("div", { class: "app-user" },
            h("a", { class: "balance-chip", href: links.wallet, title: "테스트 잔액" },
                h("span", { class: "balance-chip__label" }, "잔액"), balanceValue),
            nameSlot,
            h("a", { class: "btn btn--ghost", href: links.logout }, "로그아웃"));
    } else {
        user = h("div", { class: "app-user" },
            h("a", { class: "btn btn--ghost", href: links.join }, "회원가입"),
            h("a", { class: "btn btn--primary", href: links.login }, "로그인"));
    }

    const header = h("header", { class: "app-header" },
        h("div", { class: "app-header__inner" },
            h("a", { class: "brand", href: links.products },
                h("span", { class: "brand__dot", attrs: { "aria-hidden": "true" } }, "C"), "C2C Marketplace"),
            nav,
            user));
    container.replaceChildren(header);
}

export function setActiveNav(key) {
    Object.entries(navLinks).forEach(([name, link]) => {
        link.classList.toggle("is-active", name === key);
        if (name === key) {
            link.setAttribute("aria-current", "page");
        } else {
            link.removeAttribute("aria-current");
        }
    });
}

export function setNickname(nickname) {
    if (nickname && !session.nickname) {
        session.nickname = nickname;
        if (nameSlot) {
            nameSlot.textContent = nickname + " 님";
        }
    }
}

let walletRequest = null;

/*
 * 잔액 조회(GET /api/wallet)는 회원 ID 도 돌려주므로 대화 화면에서 내 메시지를 구분하는 데도 쓴다.
 * 지갑 행이 없으면 첫 조회가 행을 만들므로, 진행 중인 조회가 있으면 새로 보내지 않고 그 결과를 함께 쓴다.
 * 첫 조회가 동시에 두 번 가면 두 번째 요청이 행 생성에 실패한다(판단 기록 J-26).
 */
export function refreshBalance() {
    if (!session.loggedIn) {
        return Promise.resolve(null);
    }
    if (!walletRequest) {
        walletRequest = api("/wallet")
            .then((wallet) => {
                session.memberId = wallet.memberId;
                updateBalance(wallet.balance);
                return wallet;
            })
            .finally(() => { walletRequest = null; });
    }
    return walletRequest;
}

export function updateBalance(balance) {
    session.balance = balance;
    if (balanceValue) {
        balanceValue.textContent = formatWon(balance);
    }
}
