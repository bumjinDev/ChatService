<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<% request.setCharacterEncoding("UTF-8"); %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/ChatService/images/home_icon.jpg">
    <link rel="stylesheet" href="/ChatService/css/common/theme.css">
    <link rel="stylesheet" href="/ChatService/css/members/modify.css">
    <title>회원 정보 수정 · ChatService</title>
</head>

<body>
<main class="center-stage">
    <section class="auth auth--wide card rise">
        <div class="auth__head">
            <span class="brand"><span class="brand__dot">C</span>ChatService</span>
            <h1 class="auth__title">회원 정보 수정</h1>
        </div>

        <%-- 입력 id 유지: id / hidden_id / pw / pwcheck / nickname / tel / email / memberEditBtn --%>
        <div class="auth__form">
            <div class="field">
                <label for="id">아이디</label>
                <input type="text" id="id" class="input" value="${MembersVO.id}" disabled>
                <input type="hidden" id="hidden_id" value="${MembersVO.id}">
            </div>

            <div class="grid2">
                <div class="field">
                    <label for="pw">비밀번호</label>
                    <input type="password" id="pw" class="input" placeholder="비밀번호">
                </div>
                <div class="field">
                    <label for="pwcheck">비밀번호 확인</label>
                    <input type="password" id="pwcheck" class="input" placeholder="비밀번호 확인">
                </div>
            </div>

            <div class="field">
                <label for="nickname">닉네임</label>
                <input type="text" id="nickname" class="input" placeholder="닉네임">
            </div>

            <div class="grid2">
                <div class="field">
                    <label for="tel">전화번호</label>
                    <input type="text" id="tel" class="input" placeholder="전화번호">
                </div>
                <div class="field">
                    <label for="email">이메일</label>
                    <input type="text" id="email" class="input" placeholder="이메일">
                </div>
            </div>

            <button type="button" id="memberEditBtn" class="btn btn--primary btn--block btn--lg">회원 정보 수정</button>
            <button type="button" class="btn btn--ghost btn--block"
                    onclick="window.location.href='/ChatService/'">취소</button>
        </div>
    </section>
</main>
</body>
</html>
