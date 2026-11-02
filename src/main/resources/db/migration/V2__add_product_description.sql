-- Story 10: 사람이 검토한 AI 상품 설명을 저장하는 컬럼 (기존 상품은 NULL)
-- 길이는 저장 요청 검증(@Size(max = 1000))과 맞춘다
ALTER TABLE products ADD COLUMN description VARCHAR(1000) NULL;
