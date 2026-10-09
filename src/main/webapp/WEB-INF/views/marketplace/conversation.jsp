<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>대화 · C2C Marketplace</title>
<link rel="icon" href="/ChatService/images/home_icon.jpg">
<link rel="stylesheet" href="/ChatService/css/common/theme.css">
<link rel="stylesheet" href="/ChatService/css/market/market.css">
</head>
<%-- 상품별 1:1 대화 화면(GET /ChatService/conversations?conversationId=). 화면에는 데이터가 없고
     스크립트가 /api/**, /ws/** 를 호출한다. 서버가 로그인 여부를 넣지 않으므로 data-auth="required" 로 표시한다. --%>
<body class="market market--convo" data-auth="required">
<div id="header"></div>
<div id="convo" class="convo">
    <section class="convo__col convo__list-col" aria-label="내 대화 목록">
        <div class="convo__col-head">
            <h1 class="convo__col-title">내 대화</h1>
            <a class="btn btn--ghost btn--sm" href="/ChatService/#/products">상품 둘러보기</a>
        </div>
        <div class="convo__scroll"><ul id="conversationList" class="convo-list"></ul></div>
    </section>

    <section class="convo__col convo__thread-col" id="thread" aria-label="대화 내용"></section>

    <aside class="convo__col convo__info" id="info" aria-label="상품과 주문"></aside>
</div>
<script type="module" src="/ChatService/js/market/conversation-page.js"></script>
</body>
</html>
