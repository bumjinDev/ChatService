const ChatManager = {
    socket: null,
    dom: {},
    userInfo: {},
    roomNumber: null,

    init() {
        this.checkSessionStorageKey(); // ← F5 또는 최초 진입 시 sessionKey 상태 로그
        console.log("ChatManager 초기화 시작!");
        this.setupDomElements();
        if (!this.getUserAndRoomInfo()) return;
        this.setupEventListeners();
        this.connectWebSocket();
        // [여기서 세션 스토리지의 'sessionKey' 값을 콘솔에 즉시 출력]
        const sessionKey = sessionStorage.getItem("sessionKey");
        console.log("현재 세션스토리지 sessionKey 값:", sessionKey);
    },

    checkSessionStorageKey() {
        // 필요 시 구현, 없으면 무시
    },

    setupDomElements() {
        this.dom.roomNumberInput = document.getElementById("roomNumber");
        this.dom.currentPeopleInput = document.getElementById("currentPeople");
        this.dom.nickNameInput = document.getElementById("nickName");
        this.dom.exitButton = document.getElementById("exitchat");
        this.dom.chatInput = document.getElementById("inputchat");
        this.dom.sendButton = document.getElementById("chatbtn");
        this.dom.chatMessagesContainer = document.getElementById("chatMessages");
    },

    getUserAndRoomInfo() {
        this.roomNumber = this.dom.roomNumberInput ? parseInt(this.dom.roomNumberInput.value, 10) : null;
        this.userInfo.nickName = this.dom.nickNameInput ? this.dom.nickNameInput.value : null;

        if (this.roomNumber === null || isNaN(this.roomNumber) || !this.userInfo.nickName) {
            console.error("필수 정보 누락 - 대기방 이동");
            window.location.href = "/ChatService/rooms";
            return false;
        }

        console.log(`정보 로드 성공: 방 번호=${this.roomNumber}, 닉네임=${this.userInfo.nickName}`);
        return true;
    },

    setupEventListeners() {
        this.dom.exitButton.addEventListener("click", this.handleExitClick.bind(this));
        this.dom.sendButton.addEventListener("click", this.handleSendClick.bind(this));
        this.dom.chatInput.addEventListener("keypress", (event) => {
            if (event.key === "Enter") {
                event.preventDefault();
                this.handleSendClick();
            }
        });
    },

    async handleExitClick() {
        console.log(`채팅 방 나가기 버튼 클릭. 방 번호: ${this.roomNumber}`);
        if (!confirm("정말 채팅방에서 나가시겠습니까?")) return;

        const socketClosed = new Promise((resolve) => {
            this.socket.onclose = (event) => {
                console.log("WebSocket 정상 종료 감지:", event.code, event.reason);
                resolve();
            };
        });

        this.socket.close(1000, JSON.stringify({ type: "USER_EXIT", user: this.userInfo.nickName }));

        /* 명시적 종료 혹은 중복 탭으로 나가지는 경우는 실제로 완전히 나가는 것이니 SessionStorage 값을 "null" 로 초기화 해야 된다. 근거는 이후 새롭게 들어오는
        *   경우를 마련해야 되기 때문에.. */
        sessionStorage.setItem("sessionKey", "null");
        console.log('명시적 종료로 인한 sessionKey 초기화 - ' + sessionStorage.getItem("sessionKey"));

        window.location.href = "/ChatService/rooms";

        await socketClosed; // 실질적 전환 기다리려면 이 위치에서 사용
    },

    handleSendClick() {
        const chatMessage = this.dom.chatInput.value.trim();
        if (chatMessage && this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(chatMessage);
            this.dom.chatInput.value = "";
        } else if (!chatMessage) {
            console.log("입력된 메시지가 없습니다.");
        } else {
            console.warn("WebSocket 연결 상태가 아닙니다.");
        }
    },

    connectWebSocket() {
        const sessionKeyForSend = sessionStorage.getItem("sessionKey");
        // ws:// URI 내 템플릿 리터럴 백틱(`) 필수
        const websocketUrl = `ws://localhost:8186/ChatService/chat?roomNumber=${this.roomNumber}&sessionKey=${sessionKeyForSend}`;
        try {
            this.socket = new WebSocket(websocketUrl);
            console.log(`WebSocket 연결 시도: ${websocketUrl}`);

            this.socket.onopen = this.onWebSocketOpen.bind(this);
            this.socket.onmessage = this.onWebSocketMessage.bind(this);
            this.socket.onerror = this.onWebSocketError.bind(this);
            this.socket.onclose = this.onWebSocketClose.bind(this);

        } catch (error) {
            console.error("WebSocket 생성 오류:", error);
            alert("WebSocket 연결 실패. 대기방으로 이동합니다.");
            window.location.href = "/ChatService/rooms";
        }
    },

    onWebSocketOpen(event) {
        console.log("WebSocket 연결 성공!");
        console.log('연결 성공 시점 SessionKey :' + sessionStorage.getItem("sessionKey"));
    },

    onWebSocketMessage(event) {
        let messageData;
        try {
            messageData = JSON.parse(event.data);
        } catch (e) {
            console.error("JSON 파싱 실패", e);
            return;
        }

        if (messageData.type === 'SESSION_KEY' && messageData.sessionKey) {
            sessionStorage.setItem("sessionKey", messageData.sessionKey);
            console.log("신규 sessionKey를 sessionStorage에 저장 완료 :", sessionStorage.getItem("sessionKey"));
            return;
        }

        if (messageData.type === 'FORCE_DISCONNECT') {
            alert(messageData.content || "다른 탭에서 접속되어 연결이 종료됩니다.");

            /* 명시적 종료 혹은 중복 탭으로 나가지는 경우는 실제로 완전히 나가는 것이니 SessionStorage 값을 "null" 로 초기화 해야 된다. 근거는 이후 새롭게 들어오는
        *   경우를 마련해야 되기 때문에.. */
            sessionStorage.setItem("sessionKey", "null");
            console.log('명시적 종료로 인한 sessionKey 초기화 - ' + sessionStorage.getItem("sessionKey"));

            console.log("중복 탭 - sessionKey :", sessionStorage.getItem("sessionKey"));

            window.location.href = "/ChatService/rooms";
            return;
        }

        switch (messageData.type) {
            case 'CHAT':
                if (messageData.user && messageData.content !== undefined) {
                    this.displayMessage(messageData.user, messageData.content);
                }
                break;
            case 'INFO':
                if (messageData.content !== undefined) {
                    this.displayMessage('시스템', messageData.content);
                }
                break;
            case 'USER_COUNT':
                if (messageData.count !== undefined) {
                    this.dom.currentPeopleInput.value = messageData.count;
                }
                break;
            default:
                console.warn("알 수 없는 메시지 타입:", messageData.type);
        }
    },

    displayMessage(user, content) {
        const messageElement = document.createElement('div');
        messageElement.classList.add('message');

        const userSpan = document.createElement('span');
        userSpan.classList.add('user');
        userSpan.textContent = user + ':';

        const contentSpan = document.createElement('span');
        contentSpan.classList.add('content');
        contentSpan.textContent = content;

        messageElement.appendChild(userSpan);
        messageElement.appendChild(contentSpan);
        this.dom.chatMessagesContainer.appendChild(messageElement);
        this.dom.chatMessagesContainer.scrollTop = this.dom.chatMessagesContainer.scrollHeight;
    },

    onWebSocketError(errorEvent) {
        console.error("WebSocket 오류:", errorEvent);
        window.location.href = "/ChatService/rooms";
    },

    onWebSocketClose(event) {

        console.log("WebSocket 연결 종료:", event.code, event.reason);

        if (event.code === 3000 && event.reason) {
            alert("중복 접속을 시도 하셨음으로 현재 채팅방은 종료 됩니다.");

            /* 반드시 초기화 */
            sessionStorage.setItem("sessionKey", "null");
            console.log('명시적 종료로 인한 sessionKey 초기화 - ' + sessionStorage.getItem("sessionKey"));

            window.location.href = "/ChatService/rooms";
            return;
        }

        if (!event.wasClean) {
            alert("채팅 연결이 끊어졌습니다. 대기방으로 이동합니다.");
            sessionStorage.setItem("sessionKey", "null");
            console.log("신규 sessionKey를 sessionStorage에 저장:", sessionStorage.getItem("sessionKey"));
            window.location.href = "/ChatService/rooms";
        } else {
            console.log("정상 종료");
        }
    }
};

document.addEventListener("DOMContentLoaded", function () {
    ChatManager.init();
});
