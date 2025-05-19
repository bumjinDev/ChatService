<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/ChatService/images/home_icon.jpg">
    <link rel="stylesheet" href="/ChatService/css/join/join.css">
    
    <!-- Google Fonts & Bootstrap -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Courgette&display=swap" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-4bw+/aepP/YC94hEpVNVgiZdgIC5+VKNBQNGCHeKRQN+PtmoHDEXuppvnDJzQIu9" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"
            integrity="sha384-HwwvtgBNo3bZJJLYd8oVXjrBZt8cqVSpeBNS5n7C8IVInixGAoxmnlMuBnhbgrkm"
            crossorigin="anonymous"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.7.1/font/bootstrap-icons.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>

    <!-- Client JS (fetch 요청 처리용) -->
    <script src="/ChatService/js/join/join.js" defer></script>

    <title>Chat Service</title>
</head>

<body>
<div class="container">
    <header>
        <div id="logo" class="mb-5">
            <a id="logo_text" href="/ChatService">Chat Service</a>
        </div>
    </header>

    <section class="text-center" id="join-all-section">
        <div id="inputBox" class="me-3 ms-3 row">
            <div id="login-form-border" class="pt-3 pb-3">

                <div class="mt-3 mb-1" id="join-id">
                    <input type="text" id="id" name="id" class="form-control" placeholder="아이디">
                </div>
                <div class="mb-1" id="join-pw">
                    <input type="password" id="pw" name="pw" class="form-control" placeholder="비밀번호">
                </div>
                <div class="mb-5" id="join-pwCheck">
                    <input type="password" id="pw_check" class="form-control" placeholder="비밀번호 확인">
                </div>
                <div class="mb-1" id="join-nickName">
                    <input type="text" id="nickname" name="nickName" class="form-control" placeholder="닉네임">
                </div>
                <div class="mb-1" id="join-tel">
                    <input type="text" id="tel" name="tel" class="form-control" placeholder="전화번호">
                </div>
                <div class="mb-4" id="join-email">
                    <input type="text" id="email" name="email" class="form-control" placeholder="이메일">
                </div>

                <div id="logo-img">
                    <img src="/ChatService/images/home_icon.jpg" alt="">
                </div>

                <div class="mt-3 mb-5"></div>

                <div id="join-btn" class="button-login-box">
                    <input type="button" value="회원가입" id="joinBtn"
                           class="btn btn-primary btn-xs mt-3 mb-4"
                           style="width:100%; height:60px;">
                </div>
            </div>
        </div>
    </section>
</div>
</body>
</html>
