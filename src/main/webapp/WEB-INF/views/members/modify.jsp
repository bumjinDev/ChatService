<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<% request.setCharacterEncoding("UTF-8"); %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/wherehouse/images/home_icon.png">
    <link rel="stylesheet" href="/wherehouse/css/modify.css">

    <!-- JavaScript Fetch 처리 -->
    <script src="/wherehouse/js/modify.js" defer></script>

    <!-- Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Courgette&display=swap" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&display=swap" rel="stylesheet">

    <!-- Bootstrap + JQuery -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-4bw+/aepP/YC94hEpVNVgiZdgIC5+VKNBQNGCHeKRQN+PtmoHDEXuppvnDJzQIu9" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"
            integrity="sha384-HwwvtgBNo3bZJJLYd8oVXjrBZt8cqVSpeBNS5n7C8IVInixGAoxmnlMuBnhbgrkm"
            crossorigin="anonymous"></script>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>

    <title>WhereHouse</title>
</head>

<body>
<div class="container">
    <header>
        <div id="logo" class="mb-5">
            <a id="logo_text" href="/wherehouse">Where House</a>
        </div>
    </header>

    <section class="text-center" id="modify-all-section">
        <div id="inputBox" class="me-3 ms-3 row">
            <div id="login-form-border" class="pt-3 pb-3">

                <div class="mt-3 mb-1" id="modify-id">
                    <input type="text" id="id" class="form-control" value="${MembersVO.id}" disabled>
                    <input type="hidden" id="hidden_id" value="${MembersVO.id}">
                </div>

                <div class="mb-1" id="modify-pw">
                    <input type="password" id="pw" class="form-control" placeholder="비밀번호">
                </div>

                <div class="mb-5" id="modify-pwCheck">
                    <input type="password" id="pwcheck" class="form-control" placeholder="비밀번호 확인">
                </div>

                <div class="mb-1" id="modify-nickName">
                    <input type="text" id="nickname" class="form-control" placeholder="닉네임">
                </div>

                <div class="mb-1" id="modify-tel">
                    <input type="text" id="tel" class="form-control" placeholder="전화번호">
                </div>

                <div class="mb-2" id="modify-email">
                    <input type="text" id="email" class="form-control" placeholder="이메일">
                </div>

                <div id="logo-img">
                    <img src="/wherehouse/images/home_icon.png" alt="">
                </div>

                <div class="mt-3 mb-3"></div>

                <div id="modify-btn" class="button-login-box">
                    <input type="button" id="memberEditBtn" value="회원 정보 수정"
                           class="btn btn-primary btn-xs mt-3 mb-2" style="width:100%; height:60px;">
                </div>

                <div id="modify-btn" class="button-login-box">
                    <input type="button" value="취소"
                           class="btn btn-primary btn-xs mt-1 mb-4"
                           style="width:100%; height:60px;"
                           onclick="window.location.href='/wherehouse/loginSuccess'">
                </div>

            </div>
        </div>
    </section>
</div>
</body>
</html>
