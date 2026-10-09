package com.chatservice.marketplace.support;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 실제 포트로 대화 WebSocket 에 연결하는 테스트 클라이언트. 인증은 Authorization 쿠키 헤더로 보낸다. */
public final class WsTestClient {

    private WsTestClient() {
    }

    public static Connection connect(int port, TestMember member, String query) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (member != null) {
            headers.add(HttpHeaders.COOKIE, member.cookieHeader());
        }
        Connection connection = new Connection();
        URI uri = URI.create("ws://localhost:" + port + "/ChatService/ws/conversations" + query);
        connection.session = client.execute(connection.handler, headers, uri).get(5, TimeUnit.SECONDS);
        return connection;
    }

    public static final class Connection {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private WebSocketSession session;

        private final TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                closed.complete(status);
            }
        };

        /** 다음 수신 메시지. 시간 안에 없으면 null. */
        public String next(long timeoutMillis) throws InterruptedException {
            return messages.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public CloseStatus awaitClose(long timeoutMillis) throws Exception {
            return closed.get(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public boolean isOpen() {
            return session.isOpen();
        }

        public void close() throws Exception {
            if (session.isOpen()) {
                session.close();
            }
        }
    }
}
