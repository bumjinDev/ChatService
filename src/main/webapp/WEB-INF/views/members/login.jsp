<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
    String cookie = (String) request.getAttribute("token");
    // 쿠키 값이 유효하면 바로 index.jsp 요청 위한 리다이렉트 - WebController.loadMainPage(). (임의 접근 등을 차단 리다이렉션 처리)
    if (cookie != null) { response.sendRedirect("/ChatService/"); }
    else { System.out.println("no login info!"); }
%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/ChatService/images/home_icon.jpg">
    <link rel="stylesheet" href="/ChatService/css/common/theme.css">
    <link rel="stylesheet" href="/ChatService/css/login/login.css">
    <title>로그인 · ChatService</title>
</head>

<body>
<main class="center-stage">
    <section class="auth card rise">
        <div class="auth__head">
            <span class="brand"><span class="brand__dot">C</span>ChatService</span>
            <h1 class="auth__title">로그인</h1>
            <p class="auth__sub muted">계정으로 로그인하고 실시간 채팅을 시작하세요.</p>
        </div>

        <!-- 로그인 요청 : "loginFilter" 로 요청이 전달. (name=userid, name=password 유지) -->
        <form class="auth__form" action="/ChatService/login" method="post">
            <div class="field">
                <label for="userid">아이디</label>
                <input class="input" type="text" id="userid" name="userid"
                       placeholder="아이디를 입력하세요" autocomplete="username">
            </div>
            <div class="field">
                <label for="password">비밀번호</label>
                <input class="input" type="password" id="password" name="password"
                       placeholder="비밀번호를 입력하세요" autocomplete="current-password">
            </div>
            <input type="submit" value="로그인" class="btn btn--primary btn--block btn--lg">
        </form>

        <div class="auth__divider"><span>또는</span></div>

        <button type="button" class="btn btn--ghost btn--block"
                onclick="window.location.href='/ChatService/members/join'">회원가입</button>
    </section>
</main>
</body>
</html>
