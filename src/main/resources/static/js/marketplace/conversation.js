/*
 * 대화 화면 스크립트. 기존 chat.js 를 바탕으로 다음을 바꿨다.
 * - 메시지 전송은 WebSocket 이 아니라 REST(POST /api/conversations/{id}/messages)로 한다.
 * - WebSocket 은 서버가 보내는 이벤트(MESSAGE, OFFER, CONVERSATION_STATE)를 받는 데만 쓴다.
 * - 메시지와 가격 제안을 시간순으로 함께 보여 주고, messageId 와 offerId 로 중복을 거른다.
 * - 연결이 끊기면 다시 연결한 뒤 afterId 로 놓친 메시지와 최신 상세를 다시 조회한다.
 * - 종료 코드 3000(같은 회원의 다른 연결로 대체)을 받으면 안내하고 다시 연결하지 않는다.
 */
const API_BASE = "/ChatService/api";
const RECONNECT_DELAY_MS = 3000;

const ConversationPage = {
    conversationId: null,
    detail: null,
    socket: null,
    lastMessageId: 0,
    messages: new Map(),   // messageId -> 메시지
    offers: new Map(),     // offerId -> 제안
    replaced: false,
    dom: {},

    async init() {
        this.dom.messages = document.getElementById("chatMessages");
        this.dom.input = document.getElementById("inputchat");
        this.dom.sendButton = document.getElementById("chatbtn");
        this.dom.offerForm = document.getElementById("offerForm");
        this.dom.offerAmount = document.getElementById("offerAmount");
        this.dom.offerButton = document.getElementById("offerBtn");
        this.dom.notice = document.getElementById("readOnlyNotice");
        this.conversationId = Number(document.getElementById("conversationId").value);

        if (!Number.isInteger(this.conversationId) || this.conversationId <= 0) {
            alert("잘못된 대화 주소입니다.");
            window.location.href = "/ChatService/";
            return;
        }

        this.dom.sendButton.addEventListener("click", () => this.sendMessage());
        this.dom.input.addEventListener("keypress", (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                this.sendMessage();
            }
        });
        this.dom.offerButton.addEventListener("click", () => this.proposeOffer());

        await this.loadDetail();
        await this.loadMessages();
        this.connectWebSocket();
    },

    /* ---------- REST 호출 ---------- */

    async request(method, path, body) {
        const options = { method, headers: {}, credentials: "same-origin" };
        if (body !== undefined) {
            options.headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(body);
        }
        const response = await fetch(API_BASE + path, options);
        if (response.status === 401) {
            alert("로그인이 필요합니다.");
            window.location.href = "/ChatService/members/login";
            throw new Error("UNAUTHENTICATED");
        }
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const error = new Error((data && data.message) || "요청을 처리하지 못했습니다.");
            error.code = data && data.code;
            throw error;
        }
        return data;
    },

    async loadDetail() {
        try {
            this.detail = await this.request("GET", `/conversations/${this.conversationId}`);
        } catch (error) {
            alert(error.message);
            window.location.href = "/ChatService/";
            return;
        }
        this.detail.offers.forEach((offer) => this.offers.set(offer.offerId, offer));
        this.renderHeader();
        this.renderTimeline();
    },

    /* afterId 가 있으면 그보다 큰 메시지만 가져온다(재접속 후 놓친 메시지). */
    async loadMessages() {
        const query = this.lastMessageId > 0 ? `?afterId=${this.lastMessageId}` : "";
        try {
            const list = await this.request("GET", `/conversations/${this.conversationId}/messages${query}`);
            list.forEach((message) => this.addMessage(message));
            this.renderTimeline();
        } catch (error) {
            console.warn("메시지 조회 실패:", error.message);
        }
    },

    async sendMessage() {
        const content = this.dom.input.value.trim();
        if (!content) {
            return;
        }
        try {
            const saved = await this.request("POST", `/conversations/${this.conversationId}/messages`,
                { content, requestId: this.newRequestId() });
            this.dom.input.value = "";
            this.addMessage(saved);
            this.renderTimeline();
        } catch (error) {
            alert(error.message);
            if (error.code === "CONVERSATION_READ_ONLY") {
                await this.loadDetail();
            }
        }
    },

    async proposeOffer() {
        const amount = Number(this.dom.offerAmount.value);
        if (!Number.isInteger(amount) || amount <= 0) {
            alert("제안 금액은 0보다 큰 정수여야 합니다.");
            return;
        }
        try {
            const offer = await this.request("POST", `/conversations/${this.conversationId}/offers`,
                { amount, requestId: this.newRequestId() });
            this.dom.offerAmount.value = "";
            this.offers.set(offer.offerId, offer);
            this.renderTimeline();
        } catch (error) {
            alert(error.message);
        }
    },

    async respondOffer(offerId, accept) {
        try {
            const offer = await this.request("POST", `/offers/${offerId}/${accept ? "accept" : "reject"}`);
            this.offers.set(offer.offerId, Object.assign(this.offers.get(offer.offerId) || {}, offer));
            this.renderTimeline();
        } catch (error) {
            alert(error.message);
            await this.loadDetail();
        }
    },

    newRequestId() {
        return (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : String(Date.now()) + Math.random();
    },

    /* ---------- WebSocket 수신 ---------- */

    connectWebSocket() {
        const protocol = (window.location.protocol === "https:") ? "wss:" : "ws:";
        const url = `${protocol}//${window.location.host}/ChatService/ws/conversations?conversationId=${this.conversationId}`;
        this.socket = new WebSocket(url);
        this.socket.onmessage = (event) => this.onEvent(event);
        this.socket.onclose = (event) => this.onClose(event);
        this.socket.onerror = (event) => console.warn("WebSocket 오류:", event);
    },

    onEvent(event) {
        let data;
        try {
            data = JSON.parse(event.data);
        } catch (e) {
            console.error("이벤트 JSON 파싱 실패", e);
            return;
        }
        switch (data.type) {
            case "MESSAGE":
                this.addMessage(data);
                break;
            case "OFFER":
                this.offers.set(data.offerId, data);
                break;
            case "CONVERSATION_STATE":
                this.detail.writable = data.writable;
                this.detail.readOnlyReason = data.readOnlyReason;
                this.detail.product.status = data.productStatus;
                this.renderHeader();
                break;
            default:
                console.warn("알 수 없는 이벤트:", data.type);
                return;
        }
        this.renderTimeline();
    },

    async onClose(event) {
        if (event.code === 3000) {
            this.replaced = true;
            alert("다른 탭에서 이 대화에 접속해 현재 연결을 종료합니다.");
            return;
        }
        if (this.replaced) {
            return;
        }
        // 연결이 끊긴 동안 놓친 메시지와 상태는 다시 연결한 뒤 조회로 가져온다.
        setTimeout(async () => {
            await this.loadDetail();
            await this.loadMessages();
            this.connectWebSocket();
        }, RECONNECT_DELAY_MS);
    },

    /* ---------- 화면 그리기 ---------- */

    addMessage(message) {
        if (this.messages.has(message.messageId)) {
            return;
        }
        this.messages.set(message.messageId, message);
        this.lastMessageId = Math.max(this.lastMessageId, message.messageId);
    },

    renderHeader() {
        const d = this.detail;
        document.getElementById("productName").textContent = d.product.name;
        document.getElementById("productPrice").textContent = d.product.price.toLocaleString("ko-KR") + "원";
        document.getElementById("productStatus").textContent = d.product.status === "ON_SALE" ? "판매 중" : "판매 종료";
        document.getElementById("counterpart").textContent =
            d.myRole === "BUYER" ? d.seller.nickname : d.buyer.nickname;

        const readOnlyText = {
            PRODUCT_SOLD_TO_OTHER: "다른 구매자가 결제해 판매가 종료된 상품입니다. 대화는 읽기 전용입니다.",
            TRADE_FINALIZED: "거래가 끝나 대화는 읽기 전용입니다."
        };
        this.dom.notice.hidden = d.writable;
        this.dom.notice.textContent = d.writable ? "" : (readOnlyText[d.readOnlyReason] || "읽기 전용 대화입니다.");
        this.dom.input.disabled = !d.writable;
        this.dom.sendButton.disabled = !d.writable;
        this.dom.offerForm.hidden = !(d.myRole === "BUYER" && d.product.status === "ON_SALE");
    },

    /* 메시지의 createdAt, 제안의 createdAt, 제안 응답의 respondedAt 을 기준으로 시간순 정렬해 그린다. */
    renderTimeline() {
        const entries = [];
        this.messages.forEach((m) => entries.push({ time: m.createdAt, order: m.messageId, render: () => this.messageElement(m) }));
        this.offers.forEach((o) => {
            entries.push({ time: o.createdAt, order: o.offerId, render: () => this.offerElement(o) });
            if (o.respondedAt) {
                entries.push({ time: o.respondedAt, order: o.offerId, render: () => this.offerResultElement(o) });
            }
        });
        entries.sort((a, b) => (a.time < b.time ? -1 : a.time > b.time ? 1 : a.order - b.order));

        this.dom.messages.replaceChildren(...entries.map((entry) => entry.render()));
        this.dom.messages.scrollTop = this.dom.messages.scrollHeight;
    },

    messageElement(message) {
        const mine = this.detail && message.senderId !== undefined && this.isMine(message);
        const element = document.createElement("div");
        element.classList.add("message");
        if (mine) {
            element.classList.add("message--mine");
        }
        const user = document.createElement("span");
        user.classList.add("user");
        user.textContent = (message.senderNickname || message.senderId) + ":";
        const content = document.createElement("span");
        content.classList.add("content");
        content.textContent = message.content;
        element.append(user, content);
        return element;
    },

    offerElement(offer) {
        const element = document.createElement("div");
        element.classList.add("message", "message--offer");
        const title = document.createElement("span");
        title.classList.add("user");
        title.textContent = "가격 제안";
        const content = document.createElement("span");
        content.classList.add("content");
        const statusText = { PENDING: "응답 대기", ACCEPTED: "수락됨", REJECTED: "거절됨" }[offer.status] || offer.status;
        content.textContent = `${Number(offer.amount).toLocaleString("ko-KR")}원 · ${statusText}`;
        element.append(title, content);

        if (offer.status === "PENDING" && this.detail.myRole === "SELLER" && this.detail.product.status === "ON_SALE") {
            const actions = document.createElement("div");
            actions.classList.add("offer-actions");
            const accept = document.createElement("button");
            accept.classList.add("btn", "btn--primary");
            accept.textContent = "수락";
            accept.addEventListener("click", () => this.respondOffer(offer.offerId, true));
            const reject = document.createElement("button");
            reject.classList.add("btn", "btn--ghost");
            reject.textContent = "거절";
            reject.addEventListener("click", () => this.respondOffer(offer.offerId, false));
            actions.append(accept, reject);
            element.append(actions);
        }
        return element;
    },

    offerResultElement(offer) {
        const element = document.createElement("div");
        element.classList.add("message", "message--system");
        const verb = offer.status === "ACCEPTED" ? "수락" : "거절";
        element.textContent = `판매자가 ${Number(offer.amount).toLocaleString("ko-KR")}원 제안을 ${verb}했습니다.`;
        return element;
    },

    /* 응답의 닉네임으로 내 메시지인지 판단한다. 화면은 회원 ID 를 따로 받지 않는다. */
    isMine(message) {
        const myNickname = this.detail.myRole === "BUYER" ? this.detail.buyer.nickname : this.detail.seller.nickname;
        return message.senderNickname === myNickname;
    }
};

document.addEventListener("DOMContentLoaded", () => ConversationPage.init());
