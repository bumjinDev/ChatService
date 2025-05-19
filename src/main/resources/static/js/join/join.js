window.onload = function () {

    document.getElementById("joinBtn").addEventListener("click", async function () {
        const id = $('input[id="id"]').val();
        const pw = $('input[id="pw"]').val();
        const pwCheck = $('input[id="pw_check"]').val();
        const nickName = $('input[id="nickname"]').val();
        const tel = $('input[id="tel"]').val();
        const email = $('input[id="email"]').val();

        if (!id) return alert("아이디를 입력하세요");
        if (!pw) return alert("비밀번호를 입력하세요");
        if (pw !== pwCheck) return alert("비밀번호가 일치하지 않습니다.");
        if (!nickName) return alert("닉네임을 입력하세요");
        if (!tel) return alert("전화번호를 입력하세요");
        if (!email) return alert("이메일을 입력하세요");

        try {
            const response = await fetch("/ChatService/members/join", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id, pw, nickName, tel, email })
            });

            if (response.ok) {
                alert("회원가입이 정상적으로 되었습니다.");
                window.location.href = "/ChatService/members/login";
				
            } else if (response.status === 400 || response.status === 401 || response.status === 404 || response.status === 409) {
                
				const res = await response.json();
                const messages = Object.values(res).join('\n');
                alert(messages);
                
				//window.location.href = "/wherehouse/members/join";
				/* 재 입력 해야 되니 리다이렉트 대신 공백 값으로 치환 */
				document.getElementById("pw").value = '';
				document.getElementById("pw_check").value = '';
				document.getElementById("nickname").value = '';
				document.getElementById("tel").value = '';
				document.getElementById("email").value = '';
				
            } else { alert("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요."); }

        } catch (error) {
			
            console.error("서버 오류:", error);
            alert("서버와 통신 중 오류가 발생했습니다.");
        }
    });
};
