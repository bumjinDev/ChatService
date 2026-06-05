<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>채팅 방 목록 · ChatService</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" type="text/css" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" type="text/css" href="/ChatService/css/rooms/rooms.css">
</head>
<body>

<div class="rooms">

    <header class="rooms__bar">
        <span class="brand"><span class="brand__dot">C</span>채팅 방 목록</span>
        <div class="rooms__bar-right">
            <%-- 사용자 입력 정보를 서버에서 받아 표시 (id=nickname 유지) --%>
            <span class="chip">
                <span class="chip__label">닉네임</span>
                <span class="chip__value" id="nickname">${nickName}</span>
            </span>
            <button class="btn btn--ghost" id="indexBtn">메인 화면</button>
            <button class="btn btn--primary" id="createBtn">방 생성</button>
        </div>
    </header>

    <div class="rooms__panel card">
        <div class="rooms__head">
            <span class="col-num">방 번호</span>
            <span class="col-title">방 제목</span>
            <span class="col-people">참여 / 최대</span>
            <span class="col-act"></span>
        </div>

        <%-- JavaScript(roomPage.js)가 이 div 안에 .roomEntity 행들을 생성한다. --%>
        <div class="roomlist" id="roomListContainer"></div>
    </div>

</div>

<script src="/ChatService/js/rooms/roomPage.js" type="text/javascript"></script>
</body>
</html>
