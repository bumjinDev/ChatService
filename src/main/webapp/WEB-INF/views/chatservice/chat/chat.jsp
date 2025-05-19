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
	<div class="menu">
	  <label for="roomNumber">방 번호:</label>
	  <%-- 방 번호와 닉네임은 서버 모델에서 받아옵니다. --%>
	  <input type="text" id="roomNumber" class="inputspan" readonly value="${roomNumber}">

	  <label for="currentPeople">참여 인원:</label>
	  <%-- 실제 인원은 WebSocket 연결 후 서버에서 받아와 JavaScript로 업데이트 --%>
	  <input type="text" id="currentPeople" class="inputspan" readonly value="0"> <%-- 초기값 0 또는 서버에서 받은 초기 인원 --%>

	  <label for="nickName">닉네임:</label>
	  <%-- 닉네임은 서버 모델에서 받아옴. --%>
	  <input type="text" id="nickName" class="inputspan" readonly value="${nickName}">

	  <button id="exitchat">채팅 방 나가기</button>
	</div>

	<div class="showchat">
		<%-- 채팅 메시지가 동적으로 추가될 영역 --%>
		<div id="chatMessages">
			<%-- JavaScript에 의해 메시지 요소(div.message)들이 여기에 추가 --%>
		</div>

		<div class="userchat">
			<input type="text" id="inputchat" placeholder="메시지를 입력하세요.">
			<button id="chatbtn">전송</button>
		</div>
	</div>

    
	<script type="text/javascript" src="/ChatService/js/chat/chat.js"></script>
</body>
</html>