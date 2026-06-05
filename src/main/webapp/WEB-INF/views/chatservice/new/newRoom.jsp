<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>방 만들기 · ChatService</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" type="text/css" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" type="text/css" href="/ChatService/css/new/newRoom.css">
</head>
<body>
<main class="center-stage">
    <section class="auth card rise">
        <div class="auth__head">
            <span class="brand"><span class="brand__dot">C</span>ChatService</span>
            <h1 class="auth__title">새 채팅방 만들기</h1>
            <p class="auth__sub muted">방 제목과 최대 인원을 설정하세요.</p>
        </div>

        <div class="auth__form">
            <!-- 방 생성 시 입력 정보인 방 제목 및 최대 인원 수 (id 유지) -->
            <div class="field">
                <label for="title">방 제목</label>
                <input type="text" id="title" name="roomName" class="input" placeholder="방 제목을 입력하세요">
            </div>

            <div class="field">
                <label for="people">최대 인원</label>
                <select id="people" name="roomMax" class="input">
                    <option value=10>10명</option>
                    <option value=20>20명</option>
                    <option value=30>30명</option>
                    <option value=40>40명</option>
                    <option value=50>50명</option>
                    <option value=60>60명</option>
                    <option value=70>70명</option>
                    <option value=80>80명</option>
                    <option value=90>90명</option>
                    <option value=100>100명</option>
                </select>
            </div>

            <button id="createroom" class="btn btn--primary btn--block btn--lg">방 생성</button>
            <button id="exitbtn" class="btn btn--ghost btn--block">채팅 대기방으로 돌아가기</button>
        </div>
    </section>
</main>

<script src="/ChatService/js/new/newRoom.js" type="text/javascript"></script>
</body>
</html>
