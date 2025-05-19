<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>채팅 방 목록</title> <%-- 페이지 제목 변경 --%>
<link rel="stylesheet" type="text/css" href="/ChatService/css/rooms/rooms.css"> <%-- CSS 경로는 실제 프로젝트에 맞게 확인 필요 --%>
</head>
<body>

	<div class="menu">
		<button class="pageindex" id="indexBtn">메인 화면 이동</button>
		<button class="createroom" id="createBtn">방 생성</button>
		<label class="shownickname" for="nickname">닉네임</label>
		<span id="nickname">${nickName}</span><br>	<%-- 사용자 입력 정보를 서버에서 받아 표시 --%>
	</div>
	<br>
	<div class="roominfo">
		<span class="roomNumber">방 번호</span><span class="roomTitle">방 제목</span><span class="roomPeople">참여 인원 / 최대 인원</span>
	</div>
	
	<div class="roomlist" id="roomListContainer">
        <%-- JavaScript가 이 div 안에 방 목록 요소를 생성하여 채워 넣는다. --%>
	</div>

	<script src="/ChatService/js/rooms/roomPage.js" type="text/javascript"></script> <%-- JavaScript 파일 경로 확인 필요 --%>
</body>
</html>