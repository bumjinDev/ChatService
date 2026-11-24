-- 세부 작업 04 실행04 「기존 거래 상품의 수락 1건과 다수 등록 가격 결제」를 실행하기 전에 배포 서버의 DB에 실행하는 SQL
--
-- 하는 일
-- - 상품 테이블(PRODUCT)에 낙관적 락에 쓰는 버전 열(VERSION)을 추가한다.
-- - 이미 있는 상품 행의 버전은 0이 된다.
--
-- 실행하는 순서
-- - 낙관적 락을 반영한 서버를 배포하기 전에 이 SQL을 먼저 실행한다.
--   버전 열이 없는 DB에 낙관적 락을 반영한 서버를 배포하면 상품을 조회하는 기능이 모두 실패한다.
-- - 버전 열을 먼저 추가해도 지금 배포되어 있는 서버는 영향을 받지 않는다.
--
-- 같은 열은 docs/1. 프로젝트개발/2. db/ddl_marketplace.sql의 상품 테이블 정의에도 들어 있다.

ALTER TABLE PRODUCT ADD (VERSION NUMBER(19) DEFAULT 0 NOT NULL);

-- 추가한 열을 확인하는 조회
SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, NULLABLE, DATA_DEFAULT
  FROM USER_TAB_COLUMNS
 WHERE TABLE_NAME = 'PRODUCT'
   AND COLUMN_NAME = 'VERSION';
