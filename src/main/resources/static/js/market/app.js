/*
 * 메인 화면(/ChatService/)의 화면 전환. 주소의 # 뒤 경로로 화면을 고른다.
 * 상품 목록·상세는 로그인 없이 볼 수 있고, 나머지 화면은 로그인이 필요하다.
 */
import { h, emptyState } from "./dom.js";
import { session, links, renderHeader, setActiveNav, refreshBalance } from "./shell.js";
import { productListView, productDetailView } from "./views/products.js";
import { sellView } from "./views/sell.js";
import { walletView } from "./views/wallet.js";
import { orderListView, orderDetailView } from "./views/orders.js";

const routes = [
    { pattern: /^\/products$/, nav: "products", view: productListView, title: "상품" },
    { pattern: /^\/products\/(\d+)$/, nav: "products", view: productDetailView, title: "상품 상세" },
    { pattern: /^\/sell$/, nav: "sell", view: sellView, title: "판매하기", auth: true },
    { pattern: /^\/wallet$/, nav: "wallet", view: walletView, title: "지갑", auth: true },
    { pattern: /^\/purchases$/, nav: "purchases", view: (mount, ctx) => orderListView(mount, ctx, "purchases"), title: "구매 내역", auth: true },
    { pattern: /^\/sales$/, nav: "sales", view: (mount, ctx) => orderListView(mount, ctx, "sales"), title: "판매 내역", auth: true },
    { pattern: /^\/orders\/(\d+)$/, nav: null, view: orderDetailView, title: "주문 상세", auth: true }
];

const outlet = document.getElementById("app");

function parseHash() {
    const raw = window.location.hash.replace(/^#/, "") || "/products";
    const [path, queryString] = raw.split("?");
    return { path: path || "/products", query: new URLSearchParams(queryString || "") };
}

function loginGate() {
    return h("section", { class: "panel gate" },
        h("h1", { class: "page-head__title" }, "로그인이 필요합니다"),
        h("p", { class: "text-muted", style: "margin-top: 8px;" }, "판매, 지갑, 주문 내역은 로그인한 회원만 이용할 수 있습니다."),
        h("div", { class: "actions" },
            h("a", { class: "btn btn--primary", href: links.login }, "로그인"),
            h("a", { class: "btn btn--ghost", href: links.join }, "회원가입")));
}

function render() {
    const { path, query } = parseHash();
    const route = routes.find((candidate) => candidate.pattern.test(path));
    const mount = h("div", { class: "rise" });
    outlet.replaceChildren(mount);
    window.scrollTo(0, 0);

    if (!route) {
        setActiveNav(null);
        document.title = "C2C Marketplace";
        mount.replaceChildren(emptyState("페이지를 찾을 수 없습니다", "주소를 확인하세요.",
            h("a", { class: "btn btn--ghost", href: "#/products" }, "상품 목록으로")));
        return;
    }
    setActiveNav(route.nav);
    document.title = route.title + " · C2C Marketplace";
    if (route.auth && !session.loggedIn) {
        mount.replaceChildren(loginGate());
        return;
    }
    const renderedHash = window.location.hash;
    const ctx = {
        params: path.match(route.pattern).slice(1),
        query,
        navigate: (hash) => { window.location.hash = hash; },
        // 다른 화면으로 옮긴 뒤 늦게 실행된 다시 그리기(타이머 등)가 그 화면을 지우지 않게 주소가 같을 때만 다시 그린다.
        reload: () => {
            if (window.location.hash === renderedHash) {
                render();
            }
        }
    };
    // 화면이 바뀐 뒤 끝난 이전 화면의 요청은 떨어져 나간 mount 에만 그리므로 현재 화면에 영향을 주지 않는다.
    route.view(mount, ctx);
}

renderHeader(document.getElementById("header"));
if (session.loggedIn) {
    refreshBalance().catch(() => null);
}
window.addEventListener("hashchange", render);
render();
