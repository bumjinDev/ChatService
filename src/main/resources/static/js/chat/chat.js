const ChatManager = {
    socket: null,
    dom: {},
    userInfo: {},
    roomNumber: null,

    init() {
        console.log("ChatManager 초기화 시작!");
        this.setupDomElements();
        if (!this.getUserAndRoomInfo()) return;
        this.setupEventListeners();
        this.connectWebSocket();
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
        this.dom.chatInput.addEventListener("keypress", function (event) {
            if (event.key === "Enter") {
                event.preventDefault();
                this.handleSendClick();
            }
        }.bind(this));
    },

    async handleExitClick() {
        console.log(`'채팅 방 나가기' 버튼 클릭. 방 번호: ${this.roomNumber}`);
        if (!confirm("정말 채팅방에서 나가시겠습니까?")) return;

        const socketClosed = new Promise((resolve) => {
            this.socket.onclose = (event) => {
                console.log("WebSocket 정상 종료 감지:", event.code, event.reason);
                resolve();
            };
        });

        this.socket.close();

        //alert("채팅방에서 나왔습니다.");
        window.location.href = "/ChatService/rooms";
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
        const websocketUrl = `ws://localhost:8186/ChatService/chat?roomNumber=${this.roomNumber}`;
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
    },

	onWebSocketMessage(event) {
	    let messageData;
	    try {
	        messageData = JSON.parse(event.data);
	    } catch (e) {
	        console.error("JSON 파싱 실패", e);
	        return;
	    }

	    if (messageData.type === 'FORCE_DISCONNECT') {
	        alert(messageData.content || "다른 탭에서 접속되어 연결이 종료됩니다.");
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

        if (event.code === 4000 && event.reason) {
            try {
                const reasonObj = JSON.parse(event.reason);
                if (reasonObj.type === "DUPLICATE") {
                    alert("다른 탭에서 접속하여 현재 채팅방 연결이 종료되었습니다.");
                    window.location.href = "/ChatService/rooms";
                    return;
                }
            } catch (e) {
                console.warn("CloseEvent reason JSON 파싱 실패:", e);
            }
        }

        if (!event.wasClean) {
            alert("채팅 연결이 끊어졌습니다. 대기방으로 이동합니다.");
            window.location.href = "/ChatService/rooms";
        } else {
            console.log("정상 종료");
        }
    }
};

document.addEventListener("DOMContentLoaded", function () {
    ChatManager.init();
});
