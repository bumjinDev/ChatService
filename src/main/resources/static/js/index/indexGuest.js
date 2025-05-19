document.getElementById("signupBtn").addEventListener("click", function() {
    location.href = "/ChatService/members/join";	// GET 요청으로 회원 가입 페이지 요청
});

document.getElementById("loginBtn").addEventListener("click", function() {
    location.href = "/ChatService/members/login";
});