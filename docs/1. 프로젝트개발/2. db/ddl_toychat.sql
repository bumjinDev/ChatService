--------------------------------------------------------------------------------
-- ddl_toychat.sql
-- ChatService(Spring Boot 3.3.5 / Hibernate 6 / OracleDialect) → Oracle TOYCHAT 스키마 DDL
--
-- 도출 근거 : src/main/java 의 @Entity / @ElementCollection 정적 분석 결과
-- 대상 DB   : 미니PC Oracle  127.0.0.1:1521:xe  /  스키마(계정) TOYCHAT
-- 실행 계정 : TOYCHAT 본인 (계정 생성은 00_create_toychat_user.sql 을 SYSTEM 으로 먼저 실행)
--
-- ┌──────────────────────────────────────────────────────────────────────────┐
-- │ ★ 식별자 규칙 (반드시 지킬 것) — 위반 시 런타임 ORA-00942 / ORA-00904 발생  │
-- ├──────────────────────────────────────────────────────────────────────────┤
-- │ 1) ddl-auto: none → Hibernate는 테이블을 만들지도 검증하지도 않는다.        │
-- │    아래 식별자가 Hibernate가 엔티티에서 도출하는 이름과 1:1로 같아야 한다.  │
-- │ 2) physical_naming_strategy = PhysicalNamingStrategyStandardImpl 이므로     │
-- │    논리명을 언더스코어/대소문자 변환 없이 그대로 물리명으로 쓴다.           │
-- │    (예: 필드 nickName → 컬럼 nickName, joinDate → joinDate)                  │
-- │ 3) Hibernate는 식별자를 "따옴표 없이" SQL로 내보내고, Oracle은 따옴표 없는  │
-- │    식별자를 대문자로 처리한다. 따라서 이 DDL도 식별자에 큰따옴표(")를       │
-- │    절대 쓰지 않는다. 아래는 모두 대문자 = Oracle 데이터 딕셔너리 표기와 일치.│
-- │    ※ "nickName" 처럼 따옴표로 감싸 만들면 대소문자가 고정되어 매핑이 깨진다.│
-- └──────────────────────────────────────────────────────────────────────────┘
--
-- 타입 매핑 : String(length 미지정)→VARCHAR2(255), int→NUMBER(10),
--             java.sql.Date→DATE.  (이 프로젝트에는 Long/boolean/BigDecimal/
--             LocalDateTime 매핑 컬럼은 존재하지 않음)
--
-- 실행 순서 : 부모 → 자식 (FK 의존성 기준)
--   1) USERENTITY
--   2) USERENTITY_ROLES        (FK: USERID → USERENTITY.USERID)
--   3) MEMBERTBL
--   4) CHAT_ROOM_CREATION_QUEUE
--
-- 재실행(롤백 후 재적재) 시 : 아래 [DROP] 블록을 자식 → 부모 순으로 먼저 실행.
--   기본은 주석 처리되어 있으니, 깨끗하게 다시 만들 때만 주석을 해제한다.
--------------------------------------------------------------------------------


--==============================================================================
-- [DROP] 재실행용 — 필요할 때만 주석 해제 (자식 → 부모 순서)
--==============================================================================
-- DROP TABLE USERENTITY_ROLES CASCADE CONSTRAINTS PURGE;
-- DROP TABLE USERENTITY CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MEMBERTBL CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CHAT_ROOM_CREATION_QUEUE CASCADE CONSTRAINTS PURGE;


--==============================================================================
-- 1) USERENTITY  (엔티티: com.chatservice.auth.exception.dto.AuthenticationEntity)
--    Spring Security 인증용 테이블. @Table(name = "userentity")
--==============================================================================
CREATE TABLE USERENTITY (
    USERID    VARCHAR2(255)  NOT NULL,   -- 필드 userid  @Id @Column(nullable=false)
    USERNAME  VARCHAR2(255)  NOT NULL,   -- 필드 username @Column(nullable=false)
    PASSWORD  VARCHAR2(255)  NOT NULL,   -- 필드 password @Column(nullable=false)
    CONSTRAINT PK_USERENTITY PRIMARY KEY (USERID)
);


--==============================================================================
-- 2) USERENTITY_ROLES  (AuthenticationEntity.roles 의 @ElementCollection)
--    @CollectionTable(name="userentity_roles",
--                     joinColumns=@JoinColumn(name="userid", referencedColumnName="userid"))
--    @Column(name="roles") private List<String> roles;
--    ※ List(미정렬, @OrderColumn 없음) = bag → PK/UNIQUE 없음(중복 허용), 부모 FK만 존재.
--==============================================================================
CREATE TABLE USERENTITY_ROLES (
    USERID  VARCHAR2(255)  NOT NULL,     -- 조인 컬럼 → USERENTITY.USERID
    ROLES   VARCHAR2(255),               -- 원소 컬럼(권한 문자열). 제약 미지정 → NULL 허용
    CONSTRAINT FK_USERENTITY_ROLES_USERID
        FOREIGN KEY (USERID) REFERENCES USERENTITY (USERID)
);

-- (권장) FK 컬럼 인덱스 — 엔티티에서 도출된 것은 아니나, 부모 행 변경/삭제 시
-- 자식 테이블 풀스캔·락 경합을 줄이기 위한 표준 권장사항. 불필요하면 주석 처리.
CREATE INDEX IX_USERENTITY_ROLES_USERID ON USERENTITY_ROLES (USERID);


--==============================================================================
-- 3) MEMBERTBL  (엔티티: com.chatservice.user.model.MembersEntity)
--    @Table(name="membertbl"). 회원 관리 테이블.
--    네이티브 쿼리(MemberEntityRepository)도 membertbl / nickname / id 를
--    따옴표 없이 참조하므로, 아래 대문자 식별자와 정확히 일치한다.
--==============================================================================
CREATE TABLE MEMBERTBL (
    ID        VARCHAR2(255)  NOT NULL,   -- 필드 id       @Id  (PK → NOT NULL)
    PW        VARCHAR2(255),             -- 필드 pw
    NICKNAME  VARCHAR2(255),             -- 필드 nickName (StandardImpl: 변환 없음 → NICKNAME)
    TEL       VARCHAR2(255),             -- 필드 tel
    EMAIL     VARCHAR2(255),             -- 필드 email
    JOINDATE  DATE,                      -- 필드 joinDate (java.sql.Date → DATE)
    CONSTRAINT PK_MEMBERTBL PRIMARY KEY (ID)
);


--==============================================================================
-- 4) CHAT_ROOM_CREATION_QUEUE  (엔티티: com.chatservice.createroom.model.RoomQueueEntity)
--    @Table(name="chat_room_creation_queue"). 방 생성 대기열.
--    @Id @GeneratedValue(strategy = IDENTITY) → Oracle IDENTITY 컬럼.
--    primitive int 3종 → NUMBER(10) NOT NULL (자바 원시형은 NULL 불가, 조회 시 매핑 오류 방지).
--==============================================================================
CREATE TABLE CHAT_ROOM_CREATION_QUEUE (
    ROOMNUMBER     NUMBER(10)  GENERATED BY DEFAULT AS IDENTITY,  -- 필드 roomNumber (IDENTITY PK; IDENTITY는 묵시적 NOT NULL)
    ROOMTITLE      VARCHAR2(255),                                          -- 필드 roomTitle
    CURRENTPEOPLE  NUMBER(10)  NOT NULL,                                   -- 필드 currentPeople (int)
    MAXPEOPLE      NUMBER(10)  NOT NULL,                                   -- 필드 maxPeople (int)
    CONSTRAINT PK_CHAT_ROOM_CREATION_QUEUE PRIMARY KEY (ROOMNUMBER)
);


--==============================================================================
-- 적재 검증(선택) — 4개 테이블이 보이면 정상.
--==============================================================================
-- SELECT TABLE_NAME FROM USER_TABLES
--  WHERE TABLE_NAME IN ('USERENTITY','USERENTITY_ROLES','MEMBERTBL','CHAT_ROOM_CREATION_QUEUE')
--  ORDER BY TABLE_NAME;

COMMIT;
