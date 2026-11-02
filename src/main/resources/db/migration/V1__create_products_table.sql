-- Story 6~9에서 Hibernate(ddl-auto=update)가 만든 products 테이블과 같은 구조
-- 이미 테이블이 있는 기존 MySQL에서는 실행하지 않고 baseline(버전 1)으로 "적용됨" 처리한다
-- 빈 DB(테스트용 H2, 새 서버)에서는 이 파일로 테이블을 만든다
-- 한 번 적용된 마이그레이션 파일은 수정하지 않는다. 변경은 새 버전 파일로 추가한다
CREATE TABLE products (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    name  VARCHAR(255) NOT NULL,
    price INT          NOT NULL,
    PRIMARY KEY (id)
);
