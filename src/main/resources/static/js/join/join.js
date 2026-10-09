/*
 * 회원 가입 화면. POST /ChatService/members/join 에 JSON 으로 보낸다.
 * 입력 형식 오류(400)는 서버가 돌려준 fieldErrors 를 각 입력 칸 아래에 보여 준다.
 */
(function () {
    const FIELDS = [
        { key: "id", input: "id" },
        { key: "pw", input: "pw" },
        { key: "nickName", input: "nickname" },
        { key: "tel", input: "tel" },
        { key: "email", input: "email" }
    ];

    function errorSlot(input) {
        const wrap = input.closest(".field");
        let slot = wrap.querySelector(".field__error");
        if (!slot) {
            slot = document.createElement("p");
            slot.className = "field__error";
            slot.hidden = true;
            wrap.appendChild(slot);
        }
        return slot;
    }

    function clearErrors(formError) {
        document.querySelectorAll(".field__error").forEach((slot) => { slot.hidden = true; slot.textContent = ""; });
        document.querySelectorAll(".is-invalid").forEach((input) => input.classList.remove("is-invalid"));
        formError.hidden = true;
        formError.textContent = "";
    }

    function showFieldError(inputId, message) {
        const input = document.getElementById(inputId);
        input.classList.add("is-invalid");
        const slot = errorSlot(input);
        slot.textContent = message;
        slot.hidden = false;
    }

    function showFormError(formError, message) {
        formError.textContent = message;
        formError.hidden = false;
    }

    document.addEventListener("DOMContentLoaded", function () {
        const button = document.getElementById("joinBtn");
        const formError = document.getElementById("formError");
        const value = (id) => document.getElementById(id).value.trim();

        button.addEventListener("click", async function () {
            clearErrors(formError);
            const body = { id: value("id"), pw: document.getElementById("pw").value, nickName: value("nickname"), tel: value("tel"), email: value("email") };

            if (body.pw !== document.getElementById("pw_check").value) {
                showFieldError("pw_check", "비밀번호가 일치하지 않습니다.");
                return;
            }

            button.disabled = true;
            try {
                const response = await fetch("/ChatService/members/join", {
                    method: "POST",
                    headers: { "Content-Type": "application/json", Accept: "application/json" },
                    body: JSON.stringify(body)
                });
                if (response.ok) {
                    alert("회원가입이 완료되었습니다. 로그인해 주세요.");
                    window.location.href = "/ChatService/members/login";
                    return;
                }
                const data = await response.json().catch(() => null);
                if (response.status === 400 && data && data.fieldErrors) {
                    let placed = 0;
                    FIELDS.forEach(({ key, input }) => {
                        if (data.fieldErrors[key]) {
                            showFieldError(input, data.fieldErrors[key]);
                            placed++;
                        }
                    });
                    if (placed === 0) {
                        showFormError(formError, data.message || "입력값을 확인하세요.");
                    }
                    return;
                }
                // 아이디·닉네임 중복은 기존 예외의 @ResponseStatus 로 409 가 오며, 응답 본문에는 어느 값이 중복인지 담기지 않는다.
                if (response.status === 409) {
                    showFormError(formError, "이미 사용 중인 아이디 또는 닉네임입니다. 다른 값으로 다시 시도하세요.");
                    return;
                }
                showFormError(formError, "가입하지 못했습니다. 잠시 후 다시 시도하세요.");
            } catch (error) {
                console.error("회원 가입 요청 실패", error);
                showFormError(formError, "서버와 통신하지 못했습니다. 잠시 후 다시 시도하세요.");
            } finally {
                button.disabled = false;
            }
        });
    });
})();
