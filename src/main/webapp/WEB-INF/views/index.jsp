<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Chat MainPage</title>

<link rel="stylesheet" type="text/css" href="/ChatService/css/index/indexCommon.css">

<%-- userName이 "none"과 같으면 게스트 CSS, 아니면 사용자 CSS 로딩 --%>
<c:choose>
    <c:when test="${userName eq 'none'}">
        <link rel="stylesheet" type="text/css" href="/ChatService/css/index/indexGuest.css">
    </c:when>
    <c:otherwise>
        <link rel="stylesheet" type="text/css" href="/ChatService/css/index/indexUser.css">
    </c:otherwise>
</c:choose>

</head>
<body>
    <h1 class="titles">실시간 채팅 프로그램</h1>

    <%-- 정보 영역 (가로 정렬) --%>
    <div class="information">
        <%-- **여기에 div 래퍼 추가** --%>
        <div class="info-item">
            <label for="totalroom">전체 채팅 방 개수</label>
            <span id="totalroom"><c:out value="${totalRoom}" /></span>
        </div>
        <%-- **여기에 div 래퍼 추가** --%>
        <div class="info-item">
            <label for="totaluser">전체 사용자 수</label>
            <span id="totaluser"><c:out value="${totalUser}" /></span>
        </div>
    </div>

    <%-- 사용자 닉네임 표시 (정보 영역 하단) --%>
    <c:if test="${userName ne 'none'}">
        <div class="user-nickname-display">
            <label for="username">사용자</label>
            <span id="username"><c:out value="${userName}" /></span>
        </div>
    </c:if>

    <%-- 버튼 영역 --%>
    <div class="button-area">
    <c:choose>
        <c:when test="${userName eq 'none'}">
            <button class="guest-btn" id="signupBtn">회원가입</button>
            <button class="guest-btn" id="loginBtn">로그인</button>
        </c:when>
        <c:otherwise>
            <button class="user-btn" id="logoutBtn">로그아웃</button>
            <button class="user-btn" id="startBtn">실시간 채팅 시작하기</button>
        </c:otherwise>
    </c:choose>
    </div>

<%-- userName이 "none"과 같으면 게스트 JS, 아니면 사용자 JS 로딩 --%>
<c:choose>
    <c:when test="${userName eq 'none'}">
        <script src="/ChatService/js/index/indexGuest.js" type="text/javascript"></script>
    </c:when>
    <c:otherwise>
        <script src="/ChatService/js/index/indexUser.js" type="text/javascript"></script>
    </c:otherwise>
</c:choose>

</body>
</html>