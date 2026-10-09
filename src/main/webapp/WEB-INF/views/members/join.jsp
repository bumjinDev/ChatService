<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/ChatService/images/home_icon.jpg">
    <link rel="stylesheet" href="/ChatService/css/common/theme.css">
    <link rel="stylesheet" href="/ChatService/css/join/join.css">
    <link rel="stylesheet" href="/ChatService/css/market/market.css">
    <script src="/ChatService/js/join/join.js" defer></script>

    <title>회원가입 · C2C Marketplace</title>
</head>

<body>
<main class="center-stage">
    <section class="auth auth--wide card rise">
        <div class="auth__head">
            <a class="brand" href="/ChatService/"><span class="brand__dot">C</span>C2C Marketplace</a>
            <h1 class="auth__title">회원가입</h1>
            <p class="auth__sub muted">가입하면 상품을 사고팔고 판매자와 대화할 수 있습니다.</p>
        </div>

        <div class="auth__form">
            <p id="formError" class="form__error" role="alert" hidden></p>
            <div class="field">
                <label for="id">아이디</label>
                <input type="text" id="id" name="id" class="input" placeholder="4~20자">
            </div>

            <div class="grid2">
                <div class="field">
                    <label for="pw">비밀번호</label>
                    <input type="password" id="pw" name="pw" class="input" placeholder="8자 이상">
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
