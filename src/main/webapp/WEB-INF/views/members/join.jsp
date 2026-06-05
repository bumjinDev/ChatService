<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/ChatService/images/home_icon.jpg">
    <link rel="stylesheet" href="/ChatService/css/common/theme.css">
    <link rel="stylesheet" href="/ChatService/css/join/join.css">

    <!-- 기존 기능 유지: jQuery + join.js (DOM id로 값 조회) -->
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
    <script src="/ChatService/js/join/join.js" defer></script>

    <title>회원가입 · ChatService</title>
</head>

<body>
<main class="center-stage">
    <section class="auth auth--wide card rise">
        <div class="auth__head">
            <span class="brand"><span class="brand__dot">C</span>ChatService</span>
            <h1 class="auth__title">회원가입</h1>
            <p class="auth__sub muted">정보를 입력하고 ChatService에 가입하세요.</p>
        </div>

        <div class="auth__form">
            <div class="field">
                <label for="id">아이디</label>
                <input type="text" id="id" name="id" class="input" placeholder="아이디">
            </div>

            <div class="grid2">
                <div class="field">
                    <label for="pw">비밀번호</label>
                    <input type="password" id="pw" name="pw" class="input" placeholder="비밀번호">
                </div>
                <div class="field">
                    <label for="pw_check">비밀번호 확인</label>
                    <input type="password" id="pw_check" class="input" placeholder="비밀번호 확인">
                </div>
            </div>

            <div class="field">
                <label for="nickname">닉네임</label>
                <input type="text" id="nickname" name="nickName" class="input" placeholder="닉네임">
            </div>

            <div class="grid2">
                <div class="field">
                    <label for="tel">전화번호</label>
                    <input type="text" id="tel" name="tel" class="input" placeholder="010-0000-0000">
                </div>
                <div class="field">
                    <label for="email">이메일</label>
                    <input type="text" id="email" name="email" class="input" placeholder="email@example.com">
                </div>
            </div>

            <button type="button" id="joinBtn" class="btn btn--primary btn--block btn--lg">회원가입</button>
        </div>

        <p class="auth__foot muted">이미 계정이 있으신가요? <a href="/ChatService/members/login">로그인</a></p>
    </section>
</main>
</body>
</html>
