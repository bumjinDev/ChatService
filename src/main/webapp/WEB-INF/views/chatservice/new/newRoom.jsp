<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>방 생성 화면</title>
	<link rel="stylesheet" type="text/css" href="/ChatService/css/new/newRoom.css">
	<script src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.6.0/jquery.min.js" integrity="sha512-894YE6QWD5I59HgZOGReFYm4dnWc1Qt5NtvYSaNcOP+u1T9qYdvdihz0PPSiiqn/+/3e7Jo4EaG7TubfWGUrMQ==" crossorigin="anonymous" referrerpolicy="no-referrer"></script> 
</head>
<body>
	<button id="exitbtn">채팅 대기방 페이지로 이동</button>
	
	<!-- 방 생성 시 입력 정보인 방 제목 및 최대 인원 수 -->
	<label class ="title" for="title">방 제목 설정.</label>
		<input type="text" id="title" name="roomName"></input><br><br>
	<label class="people" for="people">최대 인원 수.</label>
		<select id="people" name="roomMax">
		  <option value=2>2</option>
		  <option value=3>3</option>
		  <option value=4>4</option>
		  <option value=5>5</option>
		  <option value=6>6</option>
		  <option value=7>7</option>
		  <option value=8>8</option>
		  <option value=9>9</option>
		  <option value=10>10</option>
		</select>
			
	<button id="createroom">방 생성</button>
	<script src="/ChatService/js/new/newRoom.js" type="text/javascript"></script>
</body>
	
</html>