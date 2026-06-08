# ChatService Redis 키 설계

`RedisTemplate`/`RedisHandler` 사용처를 코드에서 정적 분석해 도출한 Redis 키 설계다.
Redis는 스키마리스이므로 DDL은 없고, 아래 키 네임스페이스·자료구조·TTL 문서만 둔다.

## 연결 / 직렬화 전제

- 설정: `application.yml` → `spring.data.redis` `127.0.0.1:6379`, `password: ${REDIS_PASSWORD}` (미니PC Redis는 `requirepass` 설정됨).
- 빈: `redis.config.RedisConfig`
  - `redisTemplate()` : `RedisTemplate<String,Object>`, **키·값·해시키·해시값 모두 `StringRedisSerializer`**. 실제로 코드가 쓰는 템플릿은 이것 하나.
  - `redisTemplateAllMapData(...)` : Hash + `Jackson2JsonRedisSerializer` 빈이 정의돼 있으나 **현재 코드에서 사용처 없음**(예약/미사용).
- DB 인덱스: `spring.data.redis.database` 미지정 → 기본 **DB 0**.
- `@RedisHash`/Spring Data Redis 리포지토리 **없음** → Redis는 매핑 리포지토리가 아니라 순수 키-값 저장소(세션/토큰 캐시)로만 사용.
- 자료구조: 실제 사용되는 연산은 `opsForValue()`(String) 뿐. `RedisHandler` 가 `opsForList()`/`opsForHash()` 도 노출하지만 호출처 없음(미사용).

## 키 일람

> ⚠️ **네임스페이스 접두어가 없다.** 모든 키가 평면(flat) 키스페이스에 공존한다(`auth:token:` 같은 prefix 미사용). 아래 "키 패턴"은 접두어가 아니라 키 자체의 형태다.

| # | 키 패턴(키 자체) | 자료구조 | 값(Value) | TTL | 위치 / 용도 |
|---|---|---|---|---|---|
| 1 | `<userId>` (회원 로그인 ID) | String | 현재 활성 JWT 토큰 문자열 | **30분** | `LoginFilter.generateAndStoreJwt()` — 동일 사용자 중복 로그인 감지(단일 세션 강제). 재로그인 시 기존 매핑 삭제 후 재발급 |
| 2 | `<JWT 토큰 문자열>` | String | Base64URL 인코딩된 HMAC-SHA256 서명키 | **30분** (로그인) / **1시간** (프로필 수정 시 재등록) | 토큰별 서명키 보관. 로그인은 `LoginFilter`, 수정은 `MemberService.updateJwtToken()`. 검증 읽기는 `IndexFilter`, `JwtAuthProcessorFilter`, `MemberService.getKey()` |
| 3 | 임의 키 (API 호출자가 지정) | String | 임의 문자열 | 호출자가 `Duration` 지정 시 그 값, 없으면 무기한 | 범용 단일데이터 API. `RedisSingleDataController` `POST /ChatService/api/v1/redis/singleData/{getValue,setValue}`, `DELETE .../delete` (DTO: `key/value/duration`) |

### 키 #1 · #2 의 쌍(pair) 구조와 생명주기

로그인 시 두 키를 **양방향**으로 함께 쓴다(`LoginFilter.generateAndStoreJwt`):

```
set( userId , jwtToken , 30m )      # 키1: 사용자 → 현재 토큰 (중복 로그인 차단용)
set( jwtToken , base64(signingKey) , 30m )   # 키2: 토큰 → 서명키 (검증용)
```

- 재로그인(기존 토큰 존재 시): `delete(userId)` + `delete(existingToken)` 후 새 쌍 등록.
- 토큰 검증: 요청의 JWT로 키2를 조회해 서명키를 복원 → 서명 검증(`IndexFilter`/`JwtAuthProcessorFilter`). 키2가 없으면(만료 등) `JwtKeyNotFoundException`.
- 프로필 수정: 새 토큰으로 키2를 **1시간** TTL로 재등록하고 기존 토큰키는 `getAndDelete`(`MemberService.updateJwtToken`).
- 로그아웃: 쿠키의 `Authorization`(=JWT) 값으로 **키2** 삭제(`CookieLogoutHandler` → `redisTemplate().delete(Authorization)`). ※ 코드상 키1(`userId→token`)은 로그아웃에서 별도 삭제하지 않고 TTL(30분) 만료에 의존.

## 관리/유틸

- `RedisHandler.clearCurrentRedisDB()` → `flushDb()` (현재 DB 전체 초기화). 운영 주의.
- `RedisHandler.executeOperation(Runnable)` : 모든 쓰기 연산을 try/catch로 감싸 성공 1 / 실패 0 반환.

## 인메모리(비-Redis) 상태 — 확장/이관 검토 대상

다음은 **현재 Redis가 아니라 JVM 힙**에 있는 상태다(단일 인스턴스 전제). 미니PC 단독 배포에선 동작하지만, 다중 인스턴스/재기동 내구성이 필요해지면 Redis 이관 후보다. (지시서 3절의 "Redis/메모리 설계 분석 대상" 반영)

| 구조체 | 위치 | 자료형 | 의미 | TTL |
|---|---|---|---|---|
| `roomList` | `ChatSessionRegistry` | `Map<String, HashSet<WebSocketSession>>` | 방→브로드캐스트 세션 집합 | - |
| `roomUserSessions` | `ChatSessionRegistry` | `Map<String, Map<String,WebSocketSession>>` | 방→(userId→세션), 중복탭 판별 | - |
| `roomMap` | `ChatSessionRegistry` | `ConcurrentHashMap<Integer, ChatRoom>` | 방 상태/인원 메타 | - |
| `roomUserVOMap` | `ChatSessionRegistry` | `ConcurrentHashMap<Integer, Map<String,RoomUserStateVO>>` | 비명시적 종료(새로고침) 임시 VO | **10초**(앱 상수 `USER_TTL_MILLIS`) |
| `roomUserSessionKeyMap` | `ChatSessionRegistry` | `ConcurrentHashMap<String, Map<String,String>>` | 방→(userId→sessionKey UUID) | - |
| `roomQueueMap` | `InMemoryRoomQueueTracker` | `ConcurrentHashMap<Integer, RoomQueueVO>` | 방 생성 대기열 추적. 기동 시 `CHAT_ROOM_CREATION_QUEUE` 에서 복원 | 앱 로직(분 단위 만료 정리) |

- `WebSocketSession` 은 직렬화 대상이 아니므로 그대로 Redis 이관 불가(세션 자체는 인스턴스 로컬 유지가 불가피, 메타/카운트만 외부화 가능).
- 세션키/인원수/대기열처럼 직렬화 가능한 상태는 Redis Hash(예: `room:{roomNo}:sessionKeys` Hash[userId→uuid]) + TTL 로 이관 가능. 단 본 작업 범위(스키마 도출)에는 포함하지 않으며 참고용 제안이다.

## 운영 점검(선택)

```bash
# 미니PC에서 (requirepass 적용 환경)
redis-cli -a "$REDIS_PASSWORD" ping            # PONG
redis-cli -a "$REDIS_PASSWORD" dbsize          # 키 개수
redis-cli -a "$REDIS_PASSWORD" --scan | head   # 키 형태 확인(userId / JWT 문자열이 보임)
redis-cli -a "$REDIS_PASSWORD" ttl "<JWT>"     # 토큰키 잔여 TTL
```
