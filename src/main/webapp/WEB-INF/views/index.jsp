<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>실시간 채팅 · ChatService</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">

<link rel="stylesheet" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" href="/ChatService/css/index/indexCommon.css">

<%-- userName이 "none"과 같으면 게스트 CSS, 아니면 사용자 CSS 로딩 --%>
<c:choose>
    <c:when test="${userName eq 'none'}">
        <link rel="stylesheet" href="/ChatService/css/index/indexGuest.css">
    </c:when>
    <c:otherwise>
        <link rel="stylesheet" href="/ChatService/css/index/indexUser.css">
    </c:otherwise>
</c:choose>
</head>

<body>
<main class="center-stage">
    <section class="hero card rise">

        <div class="hero__brand">
            <span class="brand"><span class="brand__dot">C</span>ChatService</span>
        </div>

        <h1 class="hero__title">실시간 채팅 프로그램</h1>
        <p class="hero__subtitle">WebSocket 기반 실시간 채팅 — 방을 만들고 함께 대화하세요.</p>

        <%-- 정보 영역 --%>
        <div class="hero__stats">
            <div class="stat">
                <span class="stat__value" id="totalroom"><c:out value="${totalRoom}" /></span>
                <span class="stat__label">전체 채팅 방</span>
            </div>
            <div class="stat">
                <span class="stat__value" id="totaluser"><c:out value="${totalUser}" /></span>
                <span class="stat__label">전체 사용자</span>
            </div>
        </div>

        <%-- 사용자 닉네임 표시 (로그인 시) --%>
        <c:if test="${userName ne 'none'}">
            <div class="hero__user">
                <span class="hero__user-label">로그인</span>
                <span class="hero__user-name" id="username"><c:out value="${userName}" /></span>
            </div>
        </c:if>

        <%-- 버튼 영역 --%>
        <div class="hero__actions">
        <c:choose>
            <c:when test="${userName eq 'none'}">
                <button class="btn btn--ghost guest-btn" id="signupBtn">회원가입</button>
                <button class="btn btn--primary guest-btn" id="loginBtn">로그인</button>
            </c:when>
            <c:otherwise>
                <button class="btn btn--ghost user-btn" id="logoutBtn">로그아웃</button>
                <button class="btn btn--primary user-btn" id="startBtn">실시간 채팅 시작하기</button>
            </c:otherwise>
        </c:choose>
        </div>

    </section>
</main>

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
