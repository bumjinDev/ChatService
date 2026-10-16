--------------------------------------------------------------------------------
-- TOYCHAT_TEST_계정생성.sql
-- 겹침 재현 테스트에 쓸 테스트 전용 계정(스키마) TOYCHAT_TEST 를 만들고 권한을 준다.
-- 권한은 운영 계정 TOYCHAT 과 같다(docs/1. 프로젝트개발/2. db/00_create_toychat_user.sql).
--
-- 실행 계정 : SYSTEM
-- 실행 위치 : 미니PC Oracle 컨테이너(oracle-db). 앱이 TOYCHAT 으로 접속하는 곳과 같은 컨테이너에 만든다.
--             그래야 테스트 기간에 앱의 DB 계정 이름과 비밀번호만 바꿔 TOYCHAT_TEST 로 접속할 수 있다.
-- 실행 예   :
--   docker cp TOYCHAT_TEST_계정생성.sql oracle-db:/tmp/toychat_test_user.sql
--   docker exec -it oracle-db sqlplus system@localhost:1521/XE @/tmp/toychat_test_user.sql
--
-- 비밀번호는 이 파일에 적지 않는다. 실행하면 비밀번호를 입력하라는 프롬프트가 뜨고, 입력한 값은 화면에 보이지 않는다.
-- 이 비밀번호는 테스트 기간에 앱에 넘기는 DB 비밀번호와 같아야 한다.
--------------------------------------------------------------------------------

-- 치환 변수를 쓸 때 SQL*Plus 가 바뀌기 전·후 문장을 화면에 출력하지 않게 한다(비밀번호가 화면에 남지 않게).
SET VERIFY OFF

--------------------------------------------------------------------------------
-- 0. 확인: 지금 접속한 컨테이너와, TOYCHAT 이 있는 컨테이너
--    TOYCHAT 의 CON_ID 와 SHOW CON_NAME 의 결과가 같은 컨테이너인지 확인한 뒤 1 을 실행한다.
--    다른 컨테이너이면 TOYCHAT 이 있는 컨테이너로 접속해 다시 실행한다.
--------------------------------------------------------------------------------
SHOW CON_NAME
SELECT USERNAME, COMMON, CON_ID FROM CDB_USERS WHERE USERNAME IN ('TOYCHAT', 'TOYCHAT_TEST');

-- CDB 루트(CDB$ROOT)에서 1 의 CREATE USER 가 ORA-65096 으로 실패하면,
-- TOYCHAT 도 같은 컨테이너에 일반 계정(COMMON=NO)으로 있는지 위 결과로 확인한다.
-- 그렇다면 TOYCHAT 을 만들 때와 같은 방식으로, 아래 줄의 주석을 풀고 다시 실행한다.
-- ALTER SESSION SET "_ORACLE_SCRIPT" = TRUE;

--------------------------------------------------------------------------------
-- 1. 계정 생성과 권한 부여
--------------------------------------------------------------------------------
ACCEPT test_password CHAR PROMPT 'TOYCHAT_TEST password: ' HIDE

CREATE USER TOYCHAT_TEST IDENTIFIED BY "&test_password";

GRANT CONNECT, RESOURCE TO TOYCHAT_TEST;

-- 테이블과 인덱스를 USERS 테이블스페이스에 제한 없이 만들 수 있게 쿼터를 준다.
ALTER USER TOYCHAT_TEST QUOTA UNLIMITED ON USERS;

UNDEFINE test_password

--------------------------------------------------------------------------------
-- 2. 확인: 계정이 만들어졌고 잠겨 있지 않은지
--------------------------------------------------------------------------------
SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE FROM DBA_USERS WHERE USERNAME = 'TOYCHAT_TEST';

-- 다음 단계: TOYCHAT_TEST 로 접속해 ddl_toychat.sql, ddl_marketplace.sql 순서로 테이블을 만든다.
