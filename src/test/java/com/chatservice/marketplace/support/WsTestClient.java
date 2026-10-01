package com.chatservice.marketplace.support;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 테스트용 WebSocket 클라이언트. 받은 텍스트 프레임과 종료 상태를 모은다. */
public class WsTestClient extends TextWebSocketHandler {

	private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
	private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
	private WebSocketSession session;

	public static WsTestClient connect(int port, String query, String jwt) throws Exception {
		WsTestClient client = new WsTestClient();
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		if (jwt != null) {
			headers.add("Cookie", "Authorization=" + jwt);
		}
		URI uri = URI.create("ws://localhost:" + port + "/ChatService/ws/conversations" + query);
		client.session = new StandardWebSocketClient().execute(client, headers, uri).get(5, TimeUnit.SECONDS);
		return client;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		received.add(message.getPayload());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		closed.complete(status);
	}

	/** 다음 프레임을 기다린다. 시간 안에 오지 않으면 null. */
	public String next(long millis) throws InterruptedException {
		return received.poll(millis, TimeUnit.MILLISECONDS);
	}

	public CloseStatus awaitClose(long millis) throws Exception {
		return closed.get(millis, TimeUnit.MILLISECONDS);
	}

	public void sendText(String text) throws Exception {
		session.sendMessage(new TextMessage(text));
	}

	public boolean isOpen() {
		return session != null && session.isOpen();
	}

	public void close() throws Exception {
		if (session != null && session.isOpen()) {
			session.close();
		}
	}
}
