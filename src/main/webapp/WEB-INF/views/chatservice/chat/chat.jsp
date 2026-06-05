<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Chat Room - ${roomNumber}</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" type="text/css" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" type="text/css" href="/ChatService/css/chat/chat.css">
</head>
<body>
<div class="chat">

    <%-- 상단 바: 방 정보 + 나가기. (id 유지: roomNumber / currentPeople / nickName / exitchat) --%>
    <header class="chat__bar">
        <div class="chat__meta">
            <span class="chat__chip">
                <span class="chat__chip-label">방 번호</span>
                <input type="text" id="roomNumber" class="chat__chip-val" readonly value="${roomNumber}">
            </span>
            <span class="chat__chip">
                <span class="chat__chip-label">참여 인원</span>
                <input type="text" id="currentPeople" class="chat__chip-val" readonly value="0">
            </span>
            <span class="chat__chip">
                <span class="chat__chip-label">닉네임</span>
                <input type="text" id="nickName" class="chat__chip-val chat__chip-val--wide" readonly value="${nickName}">
            </span>
        </div>
        <button id="exitchat" class="btn btn--danger">채팅방 나가기</button>
    </header>

    <%-- 메시지 영역: chat.js가 #chatMessages 안에 .message 를 추가한다. --%>
    <main class="chat__body">
        <div id="chatMessages" class="chat__messages"></div>
    </main>

    <%-- 입력 영역. (id 유지: inputchat / chatbtn) --%>
    <footer class="chat__input">
        <input type="text" id="inputchat" class="input" placeholder="메시지를 입력하세요.">
        <button id="chatbtn" class="btn btn--primary">전송</button>
    </footer>

</div>
<script type="text/javascript" src="/ChatService/js/chat/chat.js"></script>
</body>
</html>
