<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>상품 대화</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" type="text/css" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" type="text/css" href="/ChatService/css/chat/chat.css">
<style>
    /* 대화 화면 전용 보조 스타일. 기본 배치는 chat.css 를 그대로 쓴다. */
    .message--mine { align-self: flex-end; border-radius: 14px 14px 4px 14px; background: #eef2ff; }
    .message--offer { border: 1px dashed var(--accent); background: #f5f3ff; }
    .message--offer .offer-actions { display: flex; gap: 8px; margin-top: 6px; }
    .message--system { align-self: center; background: transparent; border: none; color: var(--text-muted); font-size: .85rem; }
    .chat__notice { padding: 10px 14px; border-radius: var(--radius-sm); background: #fef2f2; color: var(--danger-strong); font-weight: 700; }
    .chat__offer { display: flex; gap: 10px; }
    .chat__offer .input { flex: 1; }
    .chat__chip-val { width: auto; white-space: nowrap; }
    [hidden] { display: none !important; }
</style>
</head>
<body>
<div class="chat">

    <%-- 상단 바: 상품 정보와 상대방. conversation.js 가 값을 채운다. --%>
    <header class="chat__bar">
        <div class="chat__meta">
            <span class="chat__chip">
                <span class="chat__chip-label">상품</span>
                <span id="productName" class="chat__chip-val chat__chip-val--wide"></span>
            </span>
            <span class="chat__chip">
                <span class="chat__chip-label">가격</span>
                <span id="productPrice" class="chat__chip-val"></span>
            </span>
            <span class="chat__chip">
                <span class="chat__chip-label">판매 상태</span>
                <span id="productStatus" class="chat__chip-val"></span>
            </span>
            <span class="chat__chip">
                <span class="chat__chip-label">상대방</span>
                <span id="counterpart" class="chat__chip-val chat__chip-val--wide"></span>
            </span>
        </div>
        <a href="/ChatService/" class="btn btn--ghost">메인으로</a>
    </header>

    <div id="readOnlyNotice" class="chat__notice" hidden></div>

    <%-- 메시지와 가격 제안을 시간순으로 함께 보여 준다. --%>
    <main class="chat__body">
        <div id="chatMessages" class="chat__messages"></div>
    </main>

    <%-- 가격 제안 입력: 구매 희망자에게만 보인다. --%>
    <footer id="offerForm" class="chat__input chat__offer" hidden>
        <input type="number" id="offerAmount" class="input" min="1" step="1" placeholder="제안 금액(원)">
        <button id="offerBtn" class="btn btn--ghost">가격 제안</button>
    </footer>

    <%-- 메시지 입력 --%>
    <footer class="chat__input">
        <input type="text" id="inputchat" class="input" maxlength="1000" placeholder="메시지를 입력하세요.">
        <button id="chatbtn" class="btn btn--primary">전송</button>
    </footer>

    <input type="hidden" id="conversationId" value="<c:out value='${conversationId}'/>">
</div>
<script type="text/javascript" src="/ChatService/js/marketplace/conversation.js"></script>
</body>
</html>
