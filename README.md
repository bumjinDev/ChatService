# 💬 실시간 채팅 시스템 – 구조 설계 중심 실시간 채팅 프로젝트

> Spring Boot + WebSocket + Redis 기반 실시간 채팅 시스템입니다. 동시 입장 문제, 중복 로그인, 세션 정합성 등을 구조적으로 제어하기 위한 설계를 중심으로 구현되었습니다.

---

## 🧭 목차

1. [프로젝트 소개](#1-프로젝트-소개)  
2. [아키텍처 및 설계 개요](#2-아키텍처-및-설계-개요)  
3. [주요 기능](#3-주요-기능)  
4. [폴더 구조](#4-폴더-구조)  
5. [기술 스택](#5-기술-스택)  
6. [실행 및 체험 방법](#6-실행-및-체험-방법)  
7. [문제 해결 전략](#7-문제-해결-전략)  
8. [기술 검증 결과](#8-기술-검증-결과)  
9. [향후 계획](#9-향후-계획)  
10. [문서 모음 및 상세 해설 링크](#-문서-모음-및-상세-해설-링크)

---

## 1. 프로젝트 소개

본 프로젝트는 실시간 채팅 시스템에서 발생할 수 있는 입장 동시성 문제(Race Condition), 중복 로그인에 따른 세션 충돌, WebSocket 연결 상태 불일치 등 **구조적 문제**를 해결하기 위한 설계를 중심으로 구현되었습니다. REST-WS 이원 흐름을 통합적으로 관리하며, 상태 기반 자원 제어 구조를 구성하였습니다.

---

## 2. 설계 개요

> 이 프로젝트의 설계는 단일 사용자의 단일 연결 보장, 실시간 인원 제한, 비정상 연결의 자원 반환이라는 세 가지 핵심 문제를 다루며, 구조적 동기화와 상태 정합성을 중심으로 구성되어 있습니다.

### 📌 시스템 구성 요소 요약

- 2️⃣ `WebSocketHandler` – WebSocket 연결 및 입장 최종 확정 처리
- 3️⃣ `SemaphoreRegistry` – roomId별 동시 입장 제한자 관리
- 4️⃣ `ChatSessionRegistry` – 중복 로그인 세션 관리 및 강제 종료
- 5️⃣ `InMemoryRoomQueueTracker` – WebSocket 미연결 방의 상태 추적 및 TTL 만료 처리
- 6️⃣ `RedisHandler` – JWT 서명값 중앙 저장소 및 인증 상태 동기화

### 📐 입장 흐름 시퀀스 다이어그램

> 본 시퀀스 다이어그램은 사용자가 채팅방에 입장하는 과정을 시간 순으로 시각화한 것으로, REST API 요청 → permit 확보 → WebSocket 연결 → DB 반영까지의 흐름을 구조적으로 표현합니다.
 
![architecture](https://github.com/user-attachments/assets/ca96c007-c02d-49e2-a591-572c6e454980)

- 사용자는 `/rooms/{roomId}` 경로를 통해 REST 기반 입장 요청을 전송합니다.
- 서버는 `RoomJoinService.confirmJoinRoom()`에서 `SemaphoreRegistry`를 참조하여 permit을 시도 확보합니다.
- permit 확보에 성공한 사용자만 WebSocket 연결을 시도하며, `WebSocketHandler.afterConnectionEstablished()`에서 세션이 등록되고 `joinRoom()`이 호출됩니다.
- 이 시점에서만 DB에 실제 입장 이력이 반영되며, 실패한 요청은 사전 차단됩니다.
- 이 구조는 Race Condition을 제거하고, 연결 실패 시 permit이 TTL 기반으로 회수되도록 설계되어 있습니다.


### 설계 핵심 포인트 요약

- **2단계 입장 흐름 분리**: `confirmJoinRoom()`에서 입장 조건 확인, `joinRoom()`에서 최종 입장 확정  
- **자원 고립 및 복구 구조**: `SemaphoreRegistry` + TTL 기반 permit 반환 구조로 자원 누락 방지  
- **세션 정합성 통제**: 중복 세션 차단 및 인증 위조 방지를 위한 Redis 서명 비교 구조


---

## 3. 주요 기능

### 🧩 1. 입장 동시성 제어 및 Race Condition 차단

- 🔧 **설계 목적:** 복수 사용자가 동시에 동일 방에 입장 요청 시, 정원 초과나 중복 입장으로 인한 상태 불일치를 방지
- ⚙️ **구현 구조:** REST 입장 요청 시 `RoomJoinService.confirmJoinRoom()`에서 `SemaphoreRegistry`를 통해 permit 선점 → 성공한 요청만 WebSocket 연결 이후 `joinRoom()`을 호출하여 DB 반영 수행
- 🧠 **설계 효과:** Race Condition 완전 차단. 서버는 실시간 경쟁 조건에서도 허용된 사용자 수만 입장시키고 나머지는 명확히 차단

---

### 🧱 2. 중복 로그인 감지 및 세션 충돌 방지

- 🔧 **설계 목적:** 동일한 userId가 여러 브라우저/탭/WebSocket 세션을 통해 동시에 접속하는 경우 발생하는 메시지 중복, 상태 충돌 문제 해결
- ⚙️ **구현 구조:** WebSocket 연결 시점에서 `ChatSessionRegistry`가 userId 단위로 기존 세션을 탐지 → 중복이면 기존 세션에 종료 메시지를 전송하고 강제 close 처리
- 🧠 **설계 효과:** 한 명의 사용자에 대해 오직 하나의 세션만 유지되며, 모든 클라이언트 동작이 일관된 상태에서 유지 가능

---

### 🔄 3. WebSocket 연결 여부에 따른 입장 유효성 분기

- 🔧 **설계 목적:** REST API로 입장 요청은 되었으나 WebSocket 연결이 성립되지 않을 경우, 방 정보가 DB에 반영되어 유령 방이 생성되는 문제 해결
- ⚙️ **구현 구조:** REST 요청 시 `InMemoryRoomQueueTracker`에 임시 등록만 수행 → 이후 WebSocket 연결 성공 시에만 `joinRoom()`에서 최종 입장 확정 및 DB 반영
- 🧠 **설계 효과:** 네트워크 불안정, 클라이언트 종료 등 비정상 흐름에도 시스템은 자원 낭비 없이 정상 입장 흐름만 반영

---

### 🧹 4. TTL 기반 permit 회수 및 연결 실패 복구

- 🔧 **설계 목적:** permit을 확보했지만 WebSocket 연결 실패, 브라우저 종료, 네트워크 단절 등으로 입장이 확정되지 않은 경우 자원이 고착되는 현상 방지
- ⚙️ **구현 구조:** `ChatServiceScheduler`가 `InMemoryRoomQueueTracker`의 TTL 만료 상태를 주기적으로 검사 → 만료 시 permit을 `SemaphoreRegistry`에서 release 처리하여 자원 회수
- 🧠 **설계 효과:** 정상 입장이 아닌 모든 연결 실패 흐름에서도 자원이 자동 복구되며, 시스템의 동시성 상태가 장기적으로 안정적으로 유지됨

---

### 🧷 5. Redis 기반 JWT 인증 상태 정합성 유지

- 🔧 **설계 목적:** 브라우저 탭 간 또는 서버 재접속 시, 기존 JWT 토큰의 위조, 중복 사용, 인증 무효화를 감지하고 상태를 동기화
- ⚙️ **구현 구조:** Redis에 `user:{userId}` 키로 JWT 토큰의 서명값을 저장 → WebSocket 연결 시 현재 토큰 서명과 Redis 저장값을 비교하여 불일치 시 세션 종료 처리
- 🧠 **설계 효과:** 사용자 인증 상태의 정합성이 모든 클라이언트와 서버 간에 일관되게 유지되며, 세션 탈취나 토큰 재사용 공격을 구조적으로 차단Redis 기반 JWT 인증 상태 정합성 유지


---

## 4. 폴더 구조

```
src
├── auth              # JWT 인증 필터 및 사용자 인증 처리
├── createroom        # 방 생성 요청 처리 및 대기열 관리
├── joinroom          # 입장 조건 판단 및 동시성 통제
├── websocketcore     # WebSocket 연결 및 세션 제어
├── redis             # 인증 상태 캐싱 및 Redis 연동 처리
├── scheduler         # TTL 기반 자동 정리 스케줄러
├── roomlist/export   # 방 목록 조회 및 외부 공개 구조
└── user              # 회원 가입, 로그인 및 예외 처리
```

---

## 5. 기술 스택

- Java 17, Spring Boot 3.x
- Spring WebSocket, SockJS
- Redis (Lettuce 기반 클라이언트)
- JWT 기반 인증 (Access Token + Redis 상태 연동)
- OracleDB (or H2 Local)
- Java Concurrency: `Semaphore`, `ConcurrentHashMap`, `ScheduledExecutorService`

---

## 6. 실행 및 체험 방법

> 아래 주소에서 테스트용 채팅방 입장 흐름을 직접 체험할 수 있습니다.

---

### 🧪 시각 흐름 시연 (UI/UX 기반)

아래 GIF들은 실사용자 기준에서 시스템이 실제 어떻게 작동하는지를 시각적으로 보여줍니다:

#### 🎯 1. 채팅방 입장 성공 흐름
![입장 성공](./assets/join-room-success.gif)
> 사용자가 REST로 입장 요청을 보낸 뒤, WebSocket이 연결되고 메시지를 주고받는 정상 흐름 시연

#### 🚫 2. 중복 로그인 감지 및 기존 세션 강제 종료
![중복 로그인 차단](./assets/kick-duplicate-session.gif)
> 동일 userId가 다른 브라우저에서 재접속할 경우, 기존 세션이 자동 종료되며 사용자에게 알림 발생

#### ⛔ 3. 입장 실패 (permit 부족)
![입장 거절](./assets/permit-denied.gif)
> 정원이 가득 찬 방에 입장 요청 시 서버가 거절하고 입장 불가 메시지를 반환하는 장면

---

🔗 https://test-chatservice.example.com/rooms/123

- WebSocket 연결은 View에서 자동 실행됩니다.
- JWT 없이 입장만 가능한 테스트 환경입니다.

```bash
# 로컬 실행 시:
$ redis-server
$ ./gradlew bootRun
```

---

## 7. 문제 해결 전략

| 문제 | 해결 전략 |
|------|------------|
| 최대 인원 초과 입장 | 세마포어 기반 permit 확보로 입장 자격 사전 검증 |
| REST 요청 후 연결 실패 | TTL 기반 스케줄러로 permit 자동 반환 및 방 정보 제거 |
| 동일 userId 중복 접속 | `ChatSessionRegistry`로 기존 세션 감지 및 종료 |

---

## 8. 기술 검증 결과

- [Race Condition 회피 테스트 설계 및 로그 결과](./test/입장_동시성_제어_테스트_설계.md)
- [중복 로그인 차단 및 세션 종료 검증](./test/중복_세션_제어_테스트_시나리오.md)
- [WebSocket 연결 실패 시 permit 누수 방지 결과](./test/WebSocket_예외_복구_전략.md)
- [시연 영상 (입장 흐름 + 중복 종료 처리)](https://github.com/사용자명/레포지토리명/assets/demo-kick-duplicate.gif)

> 모든 테스트는 구조적 흐름 검증을 목적으로 수동/자동 병행 테스트로 수행되었습니다.

---

## 9. 향후 계획

- Redis pub/sub 전환으로 메시지 브로드캐스트 구조 개선
- RabbitMQ 도입으로 채팅 메시지 큐 및 입장 큐 분산 처리
- WebSocket 메시지 수신 구조를 이벤트 핸들러로 추상화
- 서버 재기동 시 상태 복원 및 재동기화 구조 구축

---

## 📄 문서 모음 및 상세 해설 링크

아래 문서는 실시간 채팅 시스템의 주요 핵심 클래스에 대한 구조 설계 분석을 담은 `.md` 기반 기술 문서입니다. 각 문서는 `docs/` 디렉토리 내에 위치하며, 실제 구현 코드 기반으로 작성된 내부 구성 책임, 실행 흐름, 시스템 내 위상 등을 포함합니다:

### 🔍 구현 기반 설계 문서 목록

- [1. RoomJoinService.java](docs/) – 입장 자격 검증 및 permit 확보 흐름
- [2. SemaphoreRegistry.java](docs/) – 동시 입장 제한 세마포어 제어 구조
- [3. RoomJoinViewController.java](docs/) – 입장 REST 엔드포인트 흐름 처리
- [4. ChatTextWebSocketHandler.java](docs/) – WebSocket 연결 수락 및 입장 확정
- [5. ChatSessionRegistry.java](docs/) – 중복 로그인 탐지 및 기존 세션 종료
- [6. InMemoryRoomQueueTracker.java](docs/) – 미연결 방 상태 추적 및 TTL 기반 복원
- [7. ChatServiceScheduler.java](docs/) – permit 회수 주기 스케줄러
- [8. RedisHandler.java](docs/) – JWT 서명값 Redis 저장 및 위조 탐지 연계
- [9. JwtAuthProcessorFilter.java](docs/) – WebSocket 연결 전 JWT 검증 필터
- [10. ChatHandShakeIntercepter.java](docs/) – 초기 WebSocket 세션 컨텍스트 주입
- [11. WebSocketConfig.java](docs/) – 핸들러 및 인터셉터 설정 계층
- [12. LoginFilter.java](docs/) – 로그인 처리, JWT 발급, 쿠키 삽입
- [13. JwtAuthenticationFailureHandler.java](docs/) – 인증 실패 시 쿠키 제거 및 경고 응답 처리

> 위 문서들은 단순한 기능 설명이 아닌, 각 클래스가 시스템 자원 흐름 및 인증 구조 내에서 어떤 **설계적 책임**을 지니고 있고, 어떠한 **보안적·구조적 목적**을 달성하기 위해 존재하는지를 중심으로 서술되어 있습니다.

이를 통해 외부 참조 없이 각 기술 컴포넌트의 동작 원리, 설계 이유, 구조적 위치를 일관되게 파악할 수 있도록 구성되어 있습니다.


> 본 프로젝트는 단순한 기능 구현을 넘어서, 실시간 자원 통제와 인증 정합성 유지를 목적으로 한 **구조 설계 기반 실무형 시스템**으로 작성되었습니다.
