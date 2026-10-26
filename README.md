# Product API

상품을 등록·조회·수정·삭제하는 Spring Boot 기반 REST API입니다.
MySQL에 데이터를 저장하고, 입력값 검증과 일관된 오류 응답, 계층별 자동화 테스트를 갖추고 있습니다.

AI 코딩 에이전트(Claude Code)를 개발 전 과정에 적극 활용한 프로젝트입니다.
AI에게 코드를 맡기되, **요구사항 정의와 설계 결정은 직접 내리고, 결과는 테스트와 실제 DB로 검증**하는 방식으로 개발했습니다.

## AI 활용 개발 방식

### 1. Story 단위로 요구사항을 정의해 지시

기능을 작은 Story로 나누고, 매번 **목표 · 제약 조건 · 완료 기준 · 보고 형식**을 명시해 AI에게 전달했습니다.

> 예: "POST 요청에 id가 포함되면 400 반환 / 기존 GET·DELETE 동작 유지 /
> Entity·Service는 아직 수정하지 말 것 / 변경 파일과 테스트 결과를 보고할 것"

### 2. 설계 결정은 개발자가 직접

AI가 선택지와 장단점을 제시하면, 검토한 뒤 최종 결정은 직접 내렸습니다.
AI의 추천을 따르지 않은 경우도, 검토 후 채택한 경우도 있습니다.

| 결정 사항 | 선택지 | AI 추천 | 최종 결정 |
|---|---|---|---|
| POST 본문에 id가 포함된 경우 | 무시하고 저장 / 400으로 거절 | 무시하고 저장 | **400으로 거절** — 클라이언트가 보낸 id를 조용히 무시하면 그 번호로 저장됐다고 오해할 수 있어, 잘못된 요청임을 명확히 알리기 위해 |
| 테스트에 개발용 프로필이 적용되는 문제 | A: 개발 서버 실행 시에만 적용 / B: 테스트에서 별도 프로필 강제 | A | **검토 후 A 채택** — 증상만 막는 대신 원인(전역 환경 변수)을 제거 |
| Entity 전환 범위 | 1: 임시 호환 코드 추가 / 2: 관련 계층 일괄 전환 | 1 | **2 선택, 단 내부 5단계로 나눠 진행** — 곧 삭제될 임시 코드를 피하면서, 변경이 커지는 위험은 단계 분할로 관리 |

### 3. 변경 범위 통제

"이번 단계에서는 이 파일만 수정", "기존 데이터 삭제 금지"처럼 범위를 제한하고,
AI가 구현 중 문제를 발견하면 **임의로 수정하지 않고 먼저 보고**하도록 했습니다.

> 실제 사례: id 타입을 `int`에서 `Long`으로 바꾸는 단계에서, AI가 기존 코드의
> `Long == Long` 비교가 값이 아닌 객체를 비교해 **큰 id에서만 조용히 실패하는 버그**가 될 수 있음을 발견하고
> 구현을 멈춰 보고했습니다. 이를 검토한 뒤 전환 계획을 다시 정했습니다.

### 4. 테스트 통과에서 끝내지 않는 검증

AI가 "완료"라고 보고해도 아래 단계로 직접 확인했습니다.

1. 자동화 테스트 실행 (현재 29개)
2. 실제 서버를 띄워 API 요청으로 상태 코드 확인
3. MySQL에 직접 접속해 `SELECT`로 저장 결과 확인
4. 서버 재시작 후에도 데이터가 유지되는지 확인

### 5. 민감 정보는 AI에게 공유하지 않음

DB 비밀번호는 AI에게 알려 주지 않고 환경 변수로 직접 설정했으며,
AI는 값이 아닌 **설정 여부만** 확인하도록 했습니다.

### 6. AI가 작성한 코드를 직접 설명할 수 있도록 학습

각 Story 이후 핵심 개념(계층 구조, Entity와 DTO, 트랜잭션과 변경 감지, 테스트 환경 분리 등)을
복습 문제로 점검하며, 코드의 동작 원리와 설계 이유를 설명할 수 있도록 정리했습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.8 (Spring Web MVC, Validation) |
| Database | MySQL 8.4 |
| ORM | Spring Data JPA, Hibernate |
| Test | JUnit, Mockito, MockMvc, H2 (테스트 전용 메모리 DB) |
| Build | Maven (Maven Wrapper 포함) |
| AI Tool | Claude Code |

## API

| Method | URL | 설명 | 성공 | 실패 |
|---|---|---|---|---|
| GET | `/products` | 전체 상품 조회 | 200 | - |
| GET | `/products/{id}` | 상품 단건 조회 | 200 | 404 |
| POST | `/products` | 상품 등록 | 200 | 400 |
| PUT | `/products/{id}` | 상품 수정 | 200 | 400, 404 |
| DELETE | `/products/{id}` | 상품 삭제 | 204 | 404 |

**요청 본문 (POST, PUT)**

```json
{
  "name": "Monitor",
  "price": 200000
}
```

- `id`는 DB가 자동으로 생성합니다. 요청 본문에 `id`가 포함되면 400을 반환합니다.
- `name`: 필수, 공백만 있는 값 불가
- `price`: 필수, 0 이상

**오류 응답 형식**

모든 오류는 같은 형식으로 응답하며, stack trace 등 내부 구현 정보는 노출하지 않습니다.

```json
{
  "status": 400,
  "message": "name must not be blank"
}
```

## 프로젝트 구조

```
src/main/java/com/example/product_api
├── ProductController.java      # HTTP 요청 처리, 입력값 검증(@Valid)
├── ProductService.java         # 비즈니스 로직, 트랜잭션
├── ProductRepository.java      # DB 접근 (Spring Data JPA)
├── Product.java                # Entity (products 테이블)
├── ProductRequest.java         # 요청 DTO + 검증 규칙
├── GlobalExceptionHandler.java # 전역 예외 처리
└── ApiErrorResponse.java       # 오류 응답 형식
```

요청 흐름: `Controller → Service → Repository → MySQL`

## 실행 방법

### 1. MySQL 준비

데이터베이스와 애플리케이션 전용 계정을 만듭니다.
root 계정은 사용하지 않고, 전용 계정에는 `product_api` DB에 필요한 권한만 부여합니다.

```sql
CREATE DATABASE product_api;
CREATE USER 'product_user'@'localhost' IDENTIFIED BY '<비밀번호>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER
    ON product_api.* TO 'product_user'@'localhost';
```

### 2. 환경 변수 설정

DB 계정 정보는 소스 코드에 저장하지 않고 환경 변수로 전달합니다.

| 이름 | 설명 |
|---|---|
| `DB_USERNAME` | MySQL 사용자 (예: `product_user`) |
| `DB_PASSWORD` | MySQL 비밀번호 |

### 3. 서버 실행

```bash
# macOS / Linux
./mvnw spring-boot:run
```

```powershell
# Windows (PowerShell)
.\mvnw.cmd spring-boot:run
```

- `spring-boot:run`으로 실행하면 `local` 프로필이 적용되어, Entity를 기준으로 테이블이 자동 생성됩니다 (`ddl-auto=update`, 로컬 개발 전용).
- 기본 포트는 `8080`이며, `api.http`에 확인용 요청이 정리되어 있습니다 (VS Code REST Client).

## 테스트

```bash
# macOS / Linux
./mvnw test
```

```powershell
# Windows (PowerShell)
.\mvnw.cmd test
```

테스트는 MySQL이나 환경 변수 없이 H2 메모리 DB로 실행됩니다 (`src/test/resources/application.properties`).

| 테스트 | 방식 | 검증 내용 |
|---|---|---|
| `ProductServiceTest` | Mockito (가짜 Repository) | 조회·등록·수정·삭제, 404 처리 |
| `ProductControllerTest` | MockMvc + 가짜 Service | 상태 코드, 입력값 검증 |
| `GlobalExceptionHandlerTest` | MockMvc | 오류 응답 형식, 내부 정보 비노출 |
| `ProductApiIntegrationTest` | Spring Boot + H2 | Controller부터 DB까지 실제 저장·수정·삭제 |

## 개발 과정

기능을 작은 단위(Story)로 나누어 하나씩 구현했습니다.

| Story | 내용 |
|---|---|
| 1 | 상품 삭제 API (204 / 404) |
| 2 | 중복 상품 ID 등록 방지 (409) — Story 6에서 DB 자동 ID 도입으로 제거 |
| 3 | POST/PUT 입력값 검증 (Bean Validation) |
| 4 | 전역 예외 처리 및 오류 응답 형식 통일 |
| 5 | Controller / Service 계층 분리, 생성자 주입 |
| 6 | 메모리(ArrayList) 저장에서 MySQL + Spring Data JPA로 전환, 요청 DTO 도입, 테스트 환경 분리 |

> Story 1~6은 Git 도입 전에 진행했으며, 그 결과가 첫 커밋에 포함되어 있습니다.
> 이후 변경 사항은 Story 단위로 기록합니다.
