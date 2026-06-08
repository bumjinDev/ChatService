--------------------------------------------------------------------------------
-- 00_create_toychat_user.sql
-- TOYCHAT 계정(스키마) 생성 + 권한 부여.  ddl_toychat.sql 보다 "먼저" 실행한다.
--
-- 실행 계정 : SYSTEM (또는 SYSDBA 권한 보유 계정)
-- 접속 예   : sqlplus system/<SYSTEM_PW>@127.0.0.1:1521/xe
--
-- ※ <TOYCHAT_비밀번호> 를 실제 값으로 바꿔서 실행할 것.
--   이 값은 systemd 의 ORACLE_PASSWORD 환경변수와 "반드시 동일"해야 한다
--   (application.yml: spring.datasource.username=TOYCHAT, password=${ORACLE_PASSWORD}).
--
-- 미니PC Oracle 이 11g/XE 구조(비-CDB)면 아래 그대로 사용.
-- 만약 19c+ CDB/PDB 구조라면 먼저 ALTER SESSION SET CONTAINER=<PDB명>; 으로
-- PDB 에 접속한 뒤 실행한다(공통계정 C##... 회피).
--------------------------------------------------------------------------------

CREATE USER TOYCHAT IDENTIFIED BY "<TOYCHAT_비밀번호>";

GRANT CONNECT, RESOURCE TO TOYCHAT;

-- 테이블/인덱스가 USERS 테이블스페이스에 무제한 적재되도록 쿼터 부여
ALTER USER TOYCHAT QUOTA UNLIMITED ON USERS;

-- (선택) 기본 테이블스페이스를 USERS 로 고정하고 싶다면 주석 해제
-- ALTER USER TOYCHAT DEFAULT TABLESPACE USERS;

--------------------------------------------------------------------------------
-- 확인용
--------------------------------------------------------------------------------
-- SELECT USERNAME, ACCOUNT_STATUS, DEFAULT_TABLESPACE
--   FROM DBA_USERS WHERE USERNAME = 'TOYCHAT';
