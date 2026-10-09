<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>C2C Marketplace</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" href="/ChatService/css/market/market.css">
</head>
<%-- 메인 화면. 상품·판매·지갑·주문 화면을 # 경로로 바꿔 보여 준다(js/market/app.js).
     data-logged-in 과 data-user-name 은 MainPageController 가 넣는 로그인 여부와 닉네임이다(비로그인이면 false, 빈 값). --%>
<body class="market" data-logged-in="${loggedIn ? 'true' : 'false'}" data-user-name="<c:out value='${userName}' />">
<div id="header"></div>
<main id="app" class="page">
    <noscript><p class="notice notice--warn">이 화면은 JavaScript 가 필요합니다.</p></noscript>
</main>
<script type="module" src="/ChatService/js/market/app.js"></script>
</body>
</html>
