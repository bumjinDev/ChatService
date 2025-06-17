<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %> <%-- JSTL c:url 사용 예시 --%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Chat Room - ${roomNumber}</title> <%-- 방 번호를 타이틀에 표시 --%>

<%-- CSS 파일 경로는 Controller에서 Model에 담아 전달하거나 JSTL c:url 등으로 동적으로 생성 권장 --%>
<%-- 예시: Model.addAttribute("cssUrl", "/ChatService/css/chat/chat.css"); --%>
<%-- <link rel="stylesheet" type="text/css" href="${cssUrl}"> --%>

<%-- CSS 내용은 별도의 chat.css 파일에 있습니다. 아래 링크 태그로 포함됩니다. --%>
<link rel="stylesheet" type="text/css" href="/ChatService/css/chat/chat.css"> <%-- JSTL c:url 사용 예시 --%>

<%-- 이전 <style> 태그와 그 안의 CSS 내용은 chat.css 파일로 모두 이동되었습니다. --%>

</head>
<body>
<div class="chat-wrapper">
	<div class="menu">
		<label for="roomNumber">방 번호:</label>
		<input type="text" id="roomNumber" class="inputspan" readonly value="${roomNumber}">
		<label for="currentPeople">참여 인원:</label>
		<input type="text" id="currentPeople" class="inputspan" readonly value="0">
		<label for="nickName">닉네임:</label>
		<input type="text" id="nickName" class="inputspan" readonly value="${nickName}">
		<button id="exitchat">채팅 방 나가기</button>
	</div>
	<div class="showchat">
		<div id="chatMessages"></div>
		<div class="userchat">
			<input type="text" id="inputchat" placeholder="메시지를 입력하세요.">
			<button id="chatbtn">전송</button>
		</div>
	</div>
</div>
<script type="text/javascript" src="/ChatService/js/chat/chat.js"></script>
</body>
</html>