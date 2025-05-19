/**
 * 1. 방 생성 페이지 입력 데이터 유효성 검사
 * 2. 사용자 입력 데이터를 Fetch API를 사용하여 서버(백엔드)로 비동기 전송 (async/await 사용)
 * 3. 서버 응답 상태 코드를 확인하여 결과 처리 (성공 시 대기방 페이지 이동, 실패 시 에러 표시)
 */

 // DOMContentLoaded를 사용하여 HTML 로드가 완료된 시점에 스크립트 실행
 document.addEventListener("DOMContentLoaded", function() {

    const exitBtn = document.getElementById("exitbtn"); // JSP ID: exitbtn
    const createBtn = document.getElementById("createroom"); // JSP ID: createroom

    exitBtn.addEventListener("click", function() {
       console.log("'채팅 대기방 페이지로 이동' 버튼 클릭됨");
       window.location.href = "/ChatService/rooms"; // 예시 경로
    });


    createBtn.addEventListener("click", async function() { // <-- function 앞에 async 키워드 추가
        console.log("'방 생성' 버튼 클릭됨");

        const titleInput = document.getElementById("title");
        const peopleSelect = document.getElementById("people");

        // 요소가 없으면 여기서 중단 (이전 존재 확인과 연계)
        if (!titleInput || !peopleSelect) {
             console.error("방 제목 또는 인원 선택 요소를 찾을 수 없습니다.");
             alert("페이지 로드 중 오류 발생. 다시 시도해주세요.");
             return;
        }

        const title = titleInput.value.trim(); // trim()으로 앞뒤 공백 제거
        const people = peopleSelect.value; // select 요소의 value

        /* 입력 유효성 검사 */
        if (title.length < 1) { // 제목이 비어 있는지 확인
            alert("방 제목을 입력해주세요.");
            titleInput.focus(); // 제목 입력 필드에 포커스
            return; // 유효성 검사 실패 시 함수 실행 중단
        }

        /* 서버로 데이터 전송 (Fetch API 사용) */

        // 서버(백엔드)에서 기대하는 데이터 형식에 맞게 객체 생성
        const roomData = {
            roomTitle: title, 				// 방 제목 (서버에서 roomName으로 받을 것이라 가정)
            maxPeople: parseInt(people, 10) 	// 최대 인원 수 (정수로 변환하여 서버에 전달)
        };
        console.log("서버로 전송할 데이터:", roomData); // 전송할 데이터 로그

        const createRoomUrl = '/ChatService/rooms/new';
        try {
            const response = await fetch(createRoomUrl, {
                method: 'POST', // HTTP POST 메소드 사용 (리소스 생성)
                headers: {
                    'Content-Type': 'application/json', // 본문 데이터가 JSON 형식임을 명시
                    // 필요하다면 인증을 위한 'Authorization' 헤더 등을 여기에 추가
                },
                body: JSON.stringify(roomData) // 자바스크립트 객체를 JSON 문자열로 변환하여 요청 본문에 담기
            });
            console.log("Fetch 응답 수신. 상태 코드:", response.status);
            /* 응답 상태 코드 확인 : 채팅 방 대기열 방 내용이 생성이 되었는 지 확인.
				1) 정상 생성 : 해당 방 번호 페이지 요청, 즉 현재 방 생성 페이지에서 세션을 모두 맺고 진짜 방 페이지를 받는 것이
							 아닌, 실제 방 페이지 로드 후 해당 페이지에서 WebSocket 세션 연결, 이는 다른 기존 방 들어가는 로직과 통일하기
							 위함이며, 동시에 세션을 맺엇는 데 별개의 동작으로 방 페이지 요청하다가 이후의 네트워크 문제로 세션 객체가 붕 뜨는 상황 방지
				2) 오류 코드 : 방 생성 안되었으므로 alert 띄우기
			*/ 
			if (response.ok) { // 서버 응답 상태 코드가 성공(2xx)일 때 처리

			    // 서버 응답 본문에서 생성된 방의 고유 번호(ID)를 받아옵니다.
			    const roomNumber = await response.json();

			    // 사용자에게 방 생성 성공과 할당된 방 번호를 알려줍니다.
			    // 필요하다면 alert 대신 사용자 인터페이스에 더 자연스럽게 표시할 수 있습니다.
			    alert("방 생성 성공! 방 번호: " + roomNumber);

			    /*
			     * 방 생성 요청 처리가 성공했으므로, 사용자를 실제 채팅방 페이지로 이동시킵니다.
			     * 이동할 페이지의 URL은 '/ChatService/rooms/{방 번호}' 형태가 됩니다.
			     *
			     * 클라이언트는 해당 URL로 이동하여 새로운 페이지(채팅방 페이지)를 로드하게 됩니다.
			     * 이 채팅방 페이지가 로드되면, 그 페이지의 JavaScript가 실행되어
			     * 해당 방 번호에 맞는 WebSocket 연결을 시도하게 됩니다.
			     * (참고: 이 WebSocket 연결 시점에 서버 측에서 대기열에 있던 방을 실제 방으로 최종 확정하는 로직이 처리됩니다.)
			     */
			    window.location.href = "/ChatService/rooms/" + roomNumber; // 실제 방 페이지로 이동, 지금은 새로 생성한 방이니..무조건 그냥 됨

            } else { // 성공이 아닌 다른 상태 코드 (예: 400, 401, 500 등)
                // 에러 처리
                console.error("방 생성 실패. 상태 코드:", response.status);
                // 서버 에러 응답 본문을 읽기 위한 시도 (JSON 형식이라고 가정)
                // response.json() 자체도 비동기이므로 await 사용 필요
                let errorMessage = `방 생성 실패: 상태 코드 ${response.status}`; // 기본 에러 메시지

                try {
                     // 응답 본문을 JSON으로 파싱하는 과정도 실패할 수 있으므로 또 다른 try-catch로 감쌈
                     const errorData = await response.json();
                     // 서버 에러 응답 예시: { message: "에러 상세 내용" }
                     errorMessage = errorData.message || `알 수 없는 오류 (${response.status})`;
                     console.error("서버 에러 상세:", errorData); // 서버 에러 상세 로그
                } catch (jsonError) {
                     // 응답 본문이 JSON이 아니거나 읽을 수 없는 경우
                     console.error("에러 응답 본문 파싱 실패:", jsonError);
                     // 이 경우 기본 errorMessage를 사용
                }
                alert(errorMessage); // 사용자에게 에러 메시지 표시
            }

        } catch (error) { // <-- catch 블록: fetch 자체의 네트워크 오류 등 예상치 못한 에러 처리
            console.error("Fetch 요청 중 네트워크 또는 기타 오류 발생:", error);
            alert("요청 중 오류가 발생했습니다. 네트워크 상태를 확인하거나 나중에 다시 시도해주세요.");
        }

    });
 });