<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<% System.out.println("getAttribute('userId') : " + (String) request.getAttribute("userId")); %>

<% if((String) request.getAttribute("userId") == null) {%>
	
	<script type="text/javascript">
		alert('로그인 상태가 아니므로 초기 화면으로 돌아갑니다!');
	</script>
	
   <jsp:forward page="/"/>
<% }
   String id = (String) request.getAttribute("userId");
   String nickname = (String) request.getAttribute("userName");
%>
<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/wherehouse/images/home_icon.png">
    <link rel="stylesheet" href="/wherehouse/css/index.css">
    <script src="/wherehouse/js/index.js"></script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Courgette&display=swap" rel="stylesheet">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&display=swap" rel="stylesheet">

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css" rel="stylesheet"
        integrity="sha384-4bw+/aepP/YC94hEpVNVgiZdgIC5+VKNBQNGCHeKRQN+PtmoHDEXuppvnDJzQIu9" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"
        integrity="sha384-HwwvtgBNo3bZJJLYd8oVXjrBZt8cqVSpeBNS5n7C8IVInixGAoxmnlMuBnhbgrkm"
        crossorigin="anonymous"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.7.1/font/bootstrap-icons.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>	<!-- jquery 추가! -->
    <title>WhereHouse</title>
</head>

<body class="bg-light">
  
    <!-- 네비 바 -->
    <header>
        <nav class="navbar navbar-expand-lg fixed-top bg-light border-bottom">
            <div class="container-fluid">
                <a id="logo" class="navbar-brand ms-4 py-3" href="/wherehouse">Where House</a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarScroll"
                    aria-controls="navbarScroll" aria-expanded="false" aria-label="Toggle navigation">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse justify-content-end me-4" id="navbarScroll">
                    <ul class="navbar-nav navbar-nav-scroll" style="--bs-scroll-height: 300px;">
                        <li class="nav-item ">
                            <a class=" nav-link me-5" id="nav_btn_house_rec" href="/wherehouse/main" role="button">
                                거주지 추천
                            </a>
                        </li>
                        <li class="nav-item dropdown">
                            <a class="nav-link dropdown-toggle me-5" id="nav_btn_gu_map" href="#" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                지도
                            </a>
                            <ul class="dropdown-menu dropdown-menu-end me-4 text-end" id="nav_btn_gu_map_dropdown">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/main" id="gu_btn">지역구 지도</a>
                                </li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/main" id="detail_btn">상세
                                        지도</a>
                                </li>
                            </ul>
                        </li>
                        <li class="nav-item dropdown">
                            <a class=" nav-link dropdown-toggle me-3" id="nav_btn_data_script" href="#" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                데이터 분석
                            </a>
                            <ul class="dropdown-menu me-5 dropdown-menu-end text-end nav_btn_house_rec_dropdown" id="">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo">분석 적용 방식 페이지</a>
                                </li>
                                <hr id="navbar_hr">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#first_page">생활 안전
                                        관련</a></li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#second_page">생활
                                        편의
                                        관련</a></li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#third_page">가격
                                        관련</a></li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#fourth_page">추천
                                        결과
                                        관련</a></li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#fifth_page">인구
                                        밀집도
                                        관련</a></li>
                            </ul>
                        </li>
                                          <li class="nav-item dropdown">
                            <a class=" nav-link dropdown-toggle me-5" id="nav_btn_house_rec" href="#" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                <%=nickname %>님
                            </a>
                            <ul class="dropdown-menu me-5 dropdown-menu-end text-end nav_btn_house_rec_dropdown" id="">
                                <li>
                                	<form action="/wherehouse/members/edit" method="get">
										<input type="hidden" name="editid" value="<%=id %>" />
									    <button type="submit" class="dropdown-item nav_choose nav_item">정보수정</button>
									</form>

                                </li>
                                <li>
	                                <form action="/wherehouse/logout" id="logoutform" method="post">	<!-- session으로 가져온 Id 값을 기준으로 조회. -->
	                                    <a class="dropdown-item nav_choose nav_item" >
	                                    	<input type="submit" value="로그 아웃"  style="border: none; padding: 0;"></a>
	                                </form>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </div>
            </div>
        </nav>
    </header>

    <!-- 캐러셀 -->
    <section>
        <div id="carouselExampleCaptions" class="carousel slide" data-bs-ride="carousel">
            <div class="carousel-inner main-carousel">
                <div class="carousel-item active main-carousel-img">
                    <img src="/wherehouse/images/city9.jpg" class="d-block w-100" alt="...">
                    <div id="" class="carousel-caption mb-5 text-end inner_text">
                        <h1>MZ에게 딱 맞는 거주지가 필요할 때</h1>
                    </div>
                </div>
                <div class="carousel-item main-carousel-img">
                    <img src="/wherehouse/images/city10.jpg" class="d-block w-100" alt="...">
                    <div id="" class="carousel-caption mb-5 text-end inner_text">
                        <h1>오늘도 거주지를 찾아다니는 MZ에게</h1>
                    </div>
                </div>
                <div class="carousel-item main-carousel-img">
                    <img src="/wherehouse/images/city11.jpg" class="d-block w-100" alt="...">
                    <div id="" class="carousel-caption mb-5 text-end inner_text">
                        <h1>안전한 거주지를 찾고 싶은 당신에게</h1>
                    </div>
                </div>
                <!-- 나머지 캐러셀 아이템도 동일하게 수정 -->
            </div>
            <!-- 캐러셀 버튼 -->
            <button class="carousel-control-prev" type="button" data-bs-target="#carouselExampleCaptions"
                data-bs-slide="prev">
                <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                <span class="visually-hidden">Previous</span>
            </button>
            <button class="carousel-control-next" type="button" data-bs-target="#carouselExampleCaptions"
                data-bs-slide="next">
                <span class="carousel-control-next-icon" aria-hidden="true"></span>
                <span class="visually-hidden">Next</span>
            </button>
        </div>
    </section>

    <!-- 요약 설명 섹션 -->
    <div class="text-center mt-5 pt-5">
        <h1 id="start_text">거주지를 찾고 있는 당신에게</h1>
    </div>
    <div class="mt-5 mb-5"></div>
    <div class="row container-fluid">
        <div class="col-md-2"></div>
        <div class="col-md-2 section_logo">
            <img src="/wherehouse/images/suggest_icon_preview.png" alt="">
        </div>
        <div class="col-md-1"></div>
        <div class="col-md-2 section_logo">
            <img src="/wherehouse/images/gu_icon_preview.png" alt="">
        </div>
        <div class="col-md-1"></div>
        <div class="col-md-2 section_logo">
            <img src="/wherehouse/images/detail_icon_preview.png" alt="">
        </div>
        <div class="col-md-2"></div>
    </div>
    <div class="mt-5"></div>
    <div class="row container-fluid d-flex text-center" id="section_logo_text">
        <div class="col-md-2"></div>
        <div class="col-md-2">
            <h3>거주지 추천 서비스</h3>
        </div>
        <div class="col-md-1"></div>
        <div class="col-md-2">
            <h3>지역구 정보 서비스</h3>
        </div>
        <div class="col-md-1"></div>
        <div class="col-md-2">
            <h3>상세 정보 서비스</h3>
        </div>
        <div class="col-md-2"></div>
    </div>


    <div class="mt-3 mb-5 pt-5 pb-5"></div>

    <div id="section2">
        <!-- 소개 섹션 -->
        <div id="intro_sticky" class="pb-5"></div>
        <div id="firstPage_text" class="row container-fluid text-center  mt-3 pt-5 mb-5">
            <h1 class="col-md-3"></h1>
            <h1 class="col-md-3 intro_btn" id="intro_rec_page_btn">거주지 추천 소개</h1>
            <h1 class="col-md-3 intro_btn" id="intro_map_page_btn">지도 소개</h1>
            <h1 class="col-md-3"></h1>
        </div>
        <div id="intro_rec_page">
            <div class="container">
                <div class="row row-cols-12 row-cols-md-5 justify-content-center">
                    <div class="col">
                        <div class="card rounded-pill">
                            <div class="card-body" id="first_intro_btn"
                                style="border: 3px solid rgba(0,0,0,0.6); border-radius: 50px;">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="first_card" class="rec_intro_title"
                                        style="color: black;">
                                        사용자 입력
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card rounded-pill">
                            <div class="card-body" id="second_intro_btn">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="second_card" class="rec_intro_title">
                                        추천 결과
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card rounded-pill">
                            <div class="card-body" id="third_intro_btn">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="third_card" class="rec_intro_title">
                                        상세 비교
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <!-- 거주지 추천 설명 -->
            <div class="container mt-5 pt-2">
                <div id="first_rec_intro">
                    <div class="row">
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro6.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                        <div class="col-md-4">
                            <div class="first_introduce_text">
                                <div id="" class="site_page_innerText text-end mt-5">
                                    <h2>사용자 입력창</h2>
                                    <h5>전세, 월세 선택 후, <br>본인이 중요시하는 조건의 단계를 골라보세요!</h5>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div id="second_rec_intro">
                    <div class="row">
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro11.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                        <div class="col-md-4 mb-5">
                            <div class="first_introduce_text mt-5">
                                <div id="" class="site_page_innerText text-end">
                                    <h2>추천 결과창</h2>
                                    <h5>원하는 조건에 따라, <br> 총 3개의 지역을 추천 해드립니다!</h5>
                                    <h6>추천 지역별 평균 가격과, <br>
                                        안전성과 편의성의 정보도 받아보세요.
                                    </h6>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div id="third_rec_intro">
                    <div class="row">
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro8.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                        <div class="col-md-4 mt-5">
                            <div class="first_introduce_text">
                                <div id="" class="site_page_innerText text-end">
                                    <h2>상세 비교창</h2>
                                    <h5>비교해보고 싶은 추천 지역을 선택한 후, <br>
                                        상세 비교 버튼을 눌러보세요!
                                    </h5>
                                    <h6>선택된 지역끼리의 상세 정보를 <br> 비교하는 정보를 받아보세요.</h6>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <!-- 일반 지도 사이트 설명 -->

        <div id="intro_map_page">
            <div class="container">
                <div class="row row-cols-12 row-cols-md-5 justify-content-center">
                    <div class="col">
                        <div class="card rounded-pill">
                            <div class="card-body" id="fourth_intro_btn"
                                style="border: 3px solid rgba(0,0,0,0.6); border-radius: 50px;">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="fourth_card" class="map_intro_title"
                                        style="color: black;">
                                        인구 밀집도
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card rounded-pill" id="btn_gu_intro2">
                            <div class="card-body" id="fifth_intro_btn">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="fifth_card" class="map_intro_title">
                                        지역구 선택
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card rounded-pill" id="btn_gu_intro3">
                            <div class="card-body" id="sixth_intro_btn">
                                <h5 class="card-title">
                                    <a href="#intro_sticky" id="sixth_card" class="map_intro_title">
                                        반경 내 정보
                                    </a>
                                </h5>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="container mt-5">
                <div id="fourth_rec_intro">
                    <div class="row">
                        <div class="col-md-4 mb-5 mt-5">
                            <div class="second_introduce_text">
                                <div id="" class="site_page_innerText text-start">
                                    <h2>인구 밀집도 시각화</h2>
                                    <h5>각 지역별 인구 밀집도를 <br> 시각화한 정보를 받아보세요!</h5>
                                    <h6>총 7단계로 구분되어진 인구 밀집도 정보를 <br>참고해보는게 어떠신가요 .
                                    </h6>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro1.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                    </div>
                </div>

                <div id="fifth_rec_intro">
                    <div class="row">
                        <div class="col-md-4 mb-5 mt-5">
                            <div class="second_introduce_text">
                                <div id="" class="site_page_innerText text-start">
                                    <h2>특정 지역구 선택</h2>
                                    <h5>원하는 지역을 선택할 수도 있어요!</h5>
                                    <h6>원하는 지역을 선택한 후, <br>평균 전/월세 가격과 안전성,편의성 정보와 <br>
                                        해당 지역의 핫플레이스 정보를 받아보세요.
                                    </h6>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro9.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                    </div>
                </div>

                <div id="sixth_rec_intro">
                    <div class="row">
                        <div class="col-md-4 mb-5 mt-5">
                            <div class="second_introduce_text">
                                <div id="" class="site_page_innerText text-start">
                                    <h2>반경 내 정보</h2>
                                    <h5>핀 포인트로 <br>특정 구역의 정보를 받아보세요!</h5>
                                    <h6>원하는 구역을 클릭한 후, <br>반경 500M 내에 있는 CCTV 수와 파출소 수, <br>
                                        그리고 반경 내 편의시설들의 정보도 받아보세요.
                                    </h6>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-8 mb-5 img_content" id="">
                            <img src="/wherehouse/images/intro10.png" id=""
                                class="w-100 rounded-4 border border-secondary border-opacity-50 ani_left_to_right"
                                alt="">
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="container-fluid d-flex mt-4">
            <div class="col-md-6"></div>
            <div class="col-md-3 mt-1">
                <h2 id="score_link_text">점수 산정 방식이 궁금하다면?</h2>
            </div>
            <div class="col-md-2">
                <a href="/wherehouse/page/reinfo" class="btn btn-outline-dark rounded-pill">
                    <h3 id="score_link_btn">click!</h3>
                </a>
            </div>
        </div>
        <div class="pt-5 pb-5"></div>
    </div>
    <div class="mt-5 mb-5 pt-3 pb-3"></div>

    <!-- 카드 섹션(3) -->
    <div id="section3">
        <div class="mb-5 pt-5"></div>
        <div class="container-fluid">
            <div class="col-md- ms-5"></div>
            <div class="col-md-12 mt-5 mb-5 pb-1">
                <h1 id="" class="section_card pb-3 text-center">다양한 서비스를 경험해보세요!</h1>
                <h5 class="section_card text-center" style="color: #6c6c6c;">
                    거주지 추천, 지역구 정보, 상세 정보 등을 확인해볼 수 있어요.</h5>
            </div>
        </div>
        <div class="row container-fluid justify-content-center">
            <!-- <div class="col-md-3"></div> -->
            <div class="col-md-3">
                <div class="card border-2">
                    <div class="card-body cbody-text">
                        <h3 class="card-title mt-2">거주지 추천</h3>
                        <p class="card-text mt-4 mb-4 pb-4">
                            전세 혹은 월세를 선택한 다음,<br>
                            최대 가능한 보증금과 월세금 등을 입력하고, <br>
                            원하는 만큼 안전 단계와 편의 단계를 선택해주세요! <br>
                            저희 WhereHouse가 <br>
                            딱맞는 거주지 총 3곳을 추천해드릴게요.
                        </p>
                        <a href="/wherehouse/page/main" class="mt-3 pt-2 btn btn-light card_btn border rounded-pill"
                            id="do_suggest_btn">
                            <h4>추천 받으러 가기!</h4>
                        </a>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card border-2">
                    <div class="card-body cbody-text">
                        <h3 class="card-title mt-2">지역구 지도 정보</h3>
                        <p class="card-text mt-4 mb-4 pb-4">
                            보고 싶은 지역을 선택해주세요! <br>
                            저희 WhereHouse가 <br>
                            해당 지역의 평균 가격과 안전,편의점수 정보를 드리고, <br>
                            핫플레이스도 알려드릴게요! <br>
                            <br>
                        </p>
                        <a href="/wherehouse/page/main" class="mt-3 pt-2 btn btn-light card_btn border rounded-pill"
                            id="do_map_btn">
                            <h4>지역구 정보 보러가기!</h4>
                        </a>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card border-2">
                    <div class="card-body cbody-text">
                        <h3 class="card-title mt-2">상세 지도 정보</h3>
                        <p class="card-text mt-4 mb-4 pb-4">
                            좀 더 상세하게 정보를 받아보고 싶으신가요? <br>
                            그렇다면 핀으로 지역을 콕 집어 보세요! <br>
                            저희 WhereHouse가 <br>
                            반경 내 상세 정보들을 보여드릴게요. <br>
                            <br>
                        </p>
                        <a href="/wherehouse/page/main" class="mt-3 pt-2 btn btn-light card_btn border rounded-pill" id="do_detail_btn">
                            <h4>상세 정보 보러가기!</h4>
                        </a>
                    </div>
                </div>
            </div>
            <!-- <div class="col-md-3"></div> -->
        </div>
        <div class="mt-5 mb-5 pt-3 pb-3"></div>
    </div>

    <div class="mt-5 mb-3 pb-5"></div>

    <div id="top_btn" class="text-center">
        <a href="#" class="btn btn-outline-secondary">TOP</a>
    </div>
    <div class="mt-5 mb-5 pt-3"></div>

    <!-- <footer class="text-center mt-5">-->

    <footer>
        <div class="container">
            <footer class="py-5 border-top border-secondary border-opacity-50 mt-5">
                <div class="row">
                    <div id="" class="footer_text col-md-4 mb-3 text-left">
                        <h4 class="mb-4 ms-4">Contact</h4>
                        <ul class="nav-flex-column">
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted">
                                    <i class="bi bi-geo-alt-fill">
                                        서울시 서대문구 연희로
                                    </i>
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted">
                                    <i class="bi bi-telephone-fill">
                                         010-3370-7750
                                    </i>
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted">
                                    <i class="bi bi-envelope-fill">
                                        bumjin.dev@gmail.com
                                    </i>
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                 <a class="nav-link p-0 text-muted"
                                   	href="https://github.com/bumjinDev/wherehouse_SpringBoot" target="_blank">
                                    <i class="bi bi-github">
                                        github.com/bumjinDev/wherehouse
                                    </i>
                                </a>
                           </div>
                        </ul>
                    </div>

                    <div id="" class="footer_text col-md-4 mb-3 text-left">
                        <h4 class="mb-4 ms-4">Our Project Plan</h4>
                        <ul class="nav-flex-column">
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted"
                                    href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/1.%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B8%B0%ED%9A%8D%EC%84%9C"
                                    target='_blank'>
                                    Project Plan pres.
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted"
                                    href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/2.%EA%B0%9C%EB%B0%9C%EA%B3%84%ED%9A%8D%EC%84%9C"
                                    target='_blank'>
                                    Develop Plan
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted"
                                    href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/3.%EC%9A%94%EA%B5%AC%EC%82%AC%ED%95%AD%20%EC%A0%95%EC%9D%98%EC%84%9C_%EB%B6%84%EC%84%9D%EC%84%9C"
                                    target='_blank'>
                                    Function Specification
                                </a>
                            </div>
                            <div class="nav-item mb-2">
                                <a class="nav-link p-0 text-muted"
                                    href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/4.%ED%99%94%EB%A9%B4%20%EC%84%A4%EA%B3%84%EC%84%9C"
                                    target='_blank'>
                                    Wire Frame
                                </a>
                            </div>
                        </ul>
                    </div>
                    <div class="col-md-4 mb-3 text-center">
                        <img src="/wherehouse/images/home_icon.png" alt="" class="w-50 h-75 mt-3">
                    </div>
                </div>
            </footer>
        </div>
</body>

</html>