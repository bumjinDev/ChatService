/* 채팅 방 목록 페이지의 동작 구현. */
window.onload = function() {

    // DOM 요소 참조 가져오기
    var indexBtn = document.getElementById("indexBtn");
    var createBtn = document.getElementById("createBtn");
    var roomListContainer = document.getElementById("roomListContainer");

   	/* ==== 버튼 이벤트들 : 메인화면 이동, 방 생성 버튼 ==== */ 
    
	// index.jsp 요청
	indexBtn.addEventListener("click", function() {
        window.location.href = "/ChatService";
    });
	// 방 생성 페이지 요청
    createBtn.addEventListener("click", function() {
        window.location.href = "/ChatService/rooms/new";
    });


    /* --- async/await를 사용하여 방 목록 데이터를 가져와 동적으로 DOM 생성 --- */
    // async 키워드를 붙여 이 함수 안에서 await를 사용할 수 있게 합니다.
    async function loadAndDisplayRooms() {
        const apiUrl = '/ChatService/api/rooms';

        try {
            // fetch 호출 및 await로 응답을 기다린 후 response 변수에 담습니다.
            const response = await fetch(apiUrl, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json' 
                }
            });
            // --- 상태 코드 명시적 확인 ---
            if (!response.ok) {
                // 응답 코드가 200번대(성공)가 아니면 오류 발생
                console.error('HTTP error! status:', response.status, await response.text()); // 오류 상세 내용을 로그에 남김
               	
				let errorMessage = `방 목록을 불러오는데 실패했습니다. 상태 코드: ${response.status}`;
				/* 잘못된 사용자 요청 차단. */
				if (response.status === 401 || response.status === 403) { alert(errorMessage); }
                roomListContainer.innerHTML = `<div class="error-loading">${errorMessage}</div>`;
            }
			/* ==== 에러가 없이 올바른 사용자 ==== */
            // await로 응답 본문을 JSON으로 파싱하는 작업이 완료될 때까지 기다린 후 rooms 변수에 담습니다.
            const rooms = await response.json(); 
            // --- 파싱된 JSON 데이터(방 목록 배열)를 처리 ---
            roomListContainer.innerHTML = ''; // 기존 목록 초기화

			/* // 굳이 개설된 채팅 방이 없는 데 없으면 안 넣음
            if (!rooms || rooms.length === 0) {
                roomListContainer.innerHTML = '<div class="no-rooms">개설된 채팅방이 없습니다.</div>';
                return;
            }
			*/
            rooms.forEach(room => {
                const roomEntityDiv = document.createElement('div');
                roomEntityDiv.classList.add('roomEntity');

                const roomNumberSpan = document.createElement('span');
                roomNumberSpan.id = 'room-' + room.roomNumber; // 데이터 필드에 맞게 수정 (예: room.id, room.roomNumber 등)
                roomNumberSpan.classList.add('Number');
                roomNumberSpan.textContent = room.roomNumber; // 데이터 필드에 맞게 수정

                const roomTitleSpan = document.createElement('span');
                roomTitleSpan.classList.add('Title');
                roomTitleSpan.textContent = room.roomTitle; // 데이터 필드에 맞게 수정

                const roomPeopleSpan = document.createElement('span');
                roomPeopleSpan.classList.add('People');
                roomPeopleSpan.textContent = `${room.currentPeople} / ${room.maxPeople}`; // 데이터 필드에 맞게 수정

                const entranceBtn = document.createElement('button');
                entranceBtn.classList.add('entranceBtn');
                entranceBtn.value = room.roomNumber; // 데이터 필드에 맞게 수정
                entranceBtn.textContent = '입장';

                // 동적으로 생성된 버튼에 이벤트 리스너 연결
                entranceBtn.addEventListener("click", function() {
                    console.log("btn clicked : " + this.value);
                    window.location.href = "/ChatService/rooms/" + this.value;
                });

                roomEntityDiv.appendChild(roomNumberSpan);
                roomEntityDiv.appendChild(roomTitleSpan);
                roomEntityDiv.appendChild(roomPeopleSpan);
                roomEntityDiv.appendChild(entranceBtn);

                roomListContainer.appendChild(roomEntityDiv);
            });

        } catch (error) {
            // fetch 실패, 응답 오류 (response.ok가 false인 경우), JSON 파싱 오류 등 발생 시
            console.error('Failed to load or process room list:', error);
            // 사용자에게 오류 발생을 알림
            // 이미 response.ok 체크에서 오류 메시지를 설정했으므로 여기서는 일반 오류 메시지 표시
            if (!roomListContainer.querySelector('.error-loading')) { // 이미 오류 메시지가 없으면 추가
                 roomListContainer.innerHTML = '<div class="error-loading">방 목록을 불러오는 중 오류가 발생했습니다. 네트워크 문제 또는 서버 오류일 수 있습니다.</div>';
            }
        }
    }
    // 페이지 로드 완료 시 async 함수 호출
    loadAndDisplayRooms();
}; // window.onload 끝