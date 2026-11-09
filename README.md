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

1. 자동화 테스트 실행 (현재 108개)
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
| Framework | Spring Boot 4.0.9 (Spring Web MVC, Validation) |
| Database | MySQL 8.4 |
| ORM | Spring Data JPA, Hibernate |
| DB Migration | Flyway (`src/main/resources/db/migration`) |
| Test | JUnit, Mockito, MockMvc, H2 (테스트 전용 메모리 DB) |
| Build | Maven (Maven Wrapper 포함) |
| CI | GitHub Actions (PR·push 시 전체 테스트 자동 실행) |
| AI API | Google Gemini API (`gemini-3.5-flash-lite`, `generateContent` REST, Spring `RestClient`) |
| AI Tool | Claude Code |

## API

| Method | URL | 설명 | 성공 | 실패 |
|---|---|---|---|---|
| GET | `/products` | 전체 상품 조회 | 200 | - |
| GET | `/products/{id}` | 상품 단건 조회 | 200 | 404 |
| POST | `/products` | 상품 등록 | 201 | 400 |
| PUT | `/products/{id}` | 상품 수정 | 200 | 400, 404 |
| DELETE | `/products/{id}` | 상품 삭제 | 204 | 404 |
| POST | `/products/{id}/generate-description` | AI 상품 설명 초안 생성 (저장 안 함) | 200 | 404, 502, 503, 504 |
| PUT | `/products/{id}/description` | 검토한 상품 설명 저장 | 200 | 400, 404 |

**AI 설명 검토·저장 흐름 (Story 10)**

AI가 만든 문장을 그대로 상품 정보로 쓰지 않고, 사람이 확인·수정한 뒤 저장합니다.

```
① POST /products                           상품 등록 (description = null)
② POST /products/{id}/generate-description AI 초안 생성 → 응답만, DB 저장 안 함
③ 사람이 초안을 확인하고 필요하면 수정        (api.http 등 API 클라이언트에서)
④ PUT  /products/{id}/description          검토한 설명 저장
⑤ GET  /products/{id}                      저장된 설명과 함께 조회
```

**상품 응답 형식** (모든 상품 API 공통, `ProductResponse`)

```json
{
  "id": 1,
  "name": "Wireless Mouse",
  "price": 25000,
  "description": "가볍게 쓰기 좋은 무선 마우스입니다."
}
```

설명을 저장하지 않은 상품은 `"description": null`입니다.

**요청 본문 (POST, PUT)**

```json
{
  "name": "Monitor",
  "price": 200000
}
```

- `id`는 DB가 자동으로 생성합니다. 요청 본문에 `id`가 포함되면 400을 반환합니다.
- `name`: 필수, 공백만 있는 값 불가, 최대 255자
- `price`: 필수, 0 이상
- 상품 등록·수정으로는 설명을 바꿀 수 없습니다. 이름·가격을 수정해도 저장된 설명은 유지됩니다.

**설명 저장 (`PUT /products/{id}/description`)**

```json
{
  "description": "가볍게 쓰기 좋은 무선 마우스입니다."
}
```

- `description`: 필수, 공백만 있는 값 불가, 최대 1000자 (DB 컬럼 `VARCHAR(1000)`)
- 다시 저장하면 이전 설명을 덮어씁니다 (이력은 남기지 않음)

**AI 상품 설명 초안 생성 (`POST /products/{id}/generate-description`)**

상품 이름과 가격만으로 한국어 설명(1~3문장)을 생성합니다.
요청 본문은 없으며, 생성한 설명은 응답으로만 돌려주고 DB에는 저장하지 않습니다.

```json
{
  "productId": 1,
  "description": "가볍게 들고 다니며 쓰기 좋은 무선 마우스로, 25,000원에 만나볼 수 있습니다."
}
```

AI 구현체는 설정(`AI_CLIENT` 환경 변수)으로 고릅니다. 기본값은 Stub입니다.

| 모드 | 구현체 | 동작 |
|---|---|---|
| `stub` (기본값) | `StubAiDescriptionClient` | 실제 호출 없이 고정 문구 반환 (`Keyboard은(는) 50,000원에 판매되는 상품입니다.`) |
| `gemini` | `GeminiAiDescriptionClient` | Google Gemini API(`gemini-3.5-flash-lite`)를 실제로 호출 |

| 상황 | 상태 코드 | message |
|---|---|---|
| 없는 상품 (AI 호출 안 함) | 404 | `Product not found` |
| AI 오류 응답(인증 실패, 5xx 등), 연결 실패, 빈 응답, 잘못된 응답 구조, 끝까지 생성되지 않은 응답(`finishReason`이 `STOP`이 아님) | 502 | `AI service error` |
| AI 요청 한도 초과 (Gemini 429) | 503 | `AI service unavailable` |
| AI 응답 지연 (타임아웃) | 504 | `AI service timeout` |

**프롬프트 규칙**: 제공된 상품 이름·가격만 사용하고, 제공되지 않은 성능·규격·기능·브랜드는 지어내지 않도록 지시합니다.
규칙은 Gemini의 system instruction으로, 상품 정보는 사용자 메시지로 나눠 보냅니다.

AI 호출부는 `AiDescriptionClient` 인터페이스로 분리되어 있어(Story 8), Story 9에서는 기존 코드를 거의 바꾸지 않고 Gemini 구현체만 추가했습니다.

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
├── ProductResponse.java        # 상품 응답 DTO (id, name, price, description)
├── ProductDescriptionRequest.java    # 설명 저장 요청 DTO + 검증 규칙
├── GlobalExceptionHandler.java # 전역 예외 처리
├── ApiErrorResponse.java       # 오류 응답 형식
├── HelloController.java        # GET /hello 학습용 엔드포인트 (상품 API와 무관)
├── ProductDescriptionController.java # AI 상품 설명 생성 API
├── ProductDescriptionService.java    # 상품 조회 → AI 호출 → AI 오류를 502/503/504로 변환
├── ProductDescriptionResponse.java   # 설명 생성 응답 DTO
├── AiDescriptionClient.java          # AI 호출 인터페이스
├── StubAiDescriptionClient.java      # 실제 호출 없는 Stub 구현 (Story 8, 기본값)
├── GeminiAiDescriptionClient.java    # Google Gemini API 구현 (Story 9)
└── AiClientException.java, AiUnavailableException.java, AiTimeoutException.java # AI 실패 / 한도 초과 / 응답 지연

src/main/resources/db/migration
├── V1__create_products_table.sql      # 기존 products 테이블 (id, name, price)
└── V2__add_product_description.sql    # description VARCHAR(1000) NULL 추가 (Story 10)
```

요청 흐름: `Controller → Service → Repository → MySQL`

설명 생성 흐름: `ProductDescriptionController → ProductDescriptionService → (ProductService로 상품 조회) → AiDescriptionClient`

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

- 테이블은 **Flyway**가 `db/migration`의 SQL 파일(V1, V2 ...)을 순서대로 적용해 만들고 바꿉니다. 서버를 시작할 때 아직 적용되지 않은 파일만 실행합니다.
- Hibernate는 테이블을 바꾸지 않고, Entity와 테이블이 맞는지만 검사합니다 (`ddl-auto=validate`). 맞지 않으면 서버가 시작되지 않습니다.
- 빈 DB라면 처음 실행할 때 V1, V2가 모두 적용됩니다. Story 9 이전에 만든 기존 DB는 아래 "기존 DB를 Flyway로 전환"을 먼저 진행해야 합니다.
- 기본 포트는 `8080`이며, `api.http`에 확인용 요청이 정리되어 있습니다 (VS Code REST Client).
- 아무 설정 없이 실행하면 AI 설명 생성은 **Stub 모드**로 동작합니다 (API 키 불필요).

### 4. (선택) Gemini 모드로 실행

[Google AI Studio](https://aistudio.google.com/)에서 Gemini API 키를 발급받은 뒤, 키는 파일이 아닌 **환경 변수**로만 전달합니다.

| 이름 | 설명 |
|---|---|
| `AI_CLIENT` | `gemini`로 설정하면 Gemini 호출 (없거나 `stub`이면 Stub) |
| `GEMINI_API_KEY` | Gemini API 키. Gemini 모드인데 비어 있으면 서버가 시작되지 않습니다 (`GEMINI_API_KEY is not set`) |

```powershell
# Windows (PowerShell) - 현재 터미널 창에서만 유효
$env:AI_CLIENT = "gemini"
$env:GEMINI_API_KEY = "<발급받은 키>"
.\mvnw.cmd spring-boot:run
```

```bash
# macOS / Linux
AI_CLIENT=gemini GEMINI_API_KEY=<발급받은 키> ./mvnw spring-boot:run
```

- Stub 모드로 돌아가려면 새 터미널에서 실행하거나 `Remove-Item Env:AI_CLIENT`로 변수를 지웁니다.
- 모델 이름, API 주소, 타임아웃(연결 5초, 응답 20초)은 `src/main/resources/application.properties`의 `ai.gemini.*`에서 바꿀 수 있습니다.
- 키는 URL이 아닌 `x-goog-api-key` 헤더로 보내며, 코드·설정 파일·로그·테스트에 남기지 않습니다.
- 무료 등급은 분당·일일 요청 수 제한이 있습니다. 한도를 넘으면 Gemini가 429를 돌려주고, 이 API는 503 `AI service unavailable`로 응답합니다. 정확한 한도는 Google AI Studio의 안내를 확인하세요.

### 5. 기존 DB를 Flyway로 전환 (Story 9 이전에 만든 DB, 한 번만)

Story 9까지는 Hibernate가 `products` 테이블을 만들었기 때문에, Flyway의 기록 테이블(`flyway_schema_history`)이 없습니다.
이런 DB에서 그냥 서버를 켜면 Flyway가 **"기록 없이 데이터가 있는 DB"로 판단해 시작을 거부**합니다 (`spring.flyway.baseline-on-migrate=false`, 실수로 다른 DB를 바꾸지 않도록 하는 안전장치).

1. 백업: `mysqldump -u product_user -p product_api > product_api_backup.sql`
2. 현재 테이블이 V1과 같은지 확인: `SHOW CREATE TABLE products;` (`id BIGINT AUTO_INCREMENT`, `name VARCHAR(255) NOT NULL`, `price INT NOT NULL`)
3. **이번 한 번만** baseline 옵션을 켜고 실행합니다. 기존 테이블을 V1로 기록하고 V2(description 컬럼 추가)만 적용합니다.
   ```powershell
   $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "true"
   .\mvnw.cmd spring-boot:run
   ```
4. 확인 후 옵션을 지웁니다: `Remove-Item Env:SPRING_FLYWAY_BASELINE_ON_MIGRATE`
5. 확인: `SELECT version, description, success FROM flyway_schema_history;` (1 = baseline, 2 = add product description), `DESCRIBE products;`

> 한 번 적용된 마이그레이션 파일(V1, V2)은 수정하지 않습니다. 테이블 변경이 필요하면 새 버전 파일(V3 ...)을 추가합니다.

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
테스트 DB의 테이블도 실제 서버와 같은 Flyway 마이그레이션(V1, V2)으로 만들어집니다.

`main` 브랜치 대상 PR을 만들거나 `main`에 push하면 GitHub Actions가 같은 테스트를 자동으로 실행합니다 (`.github/workflows/ci.yml`).
결과는 PR의 Checks 또는 저장소의 Actions 탭에서 확인할 수 있습니다.

| 테스트 | 방식 | 검증 내용 |
|---|---|---|
| `ProductServiceTest` | Mockito (가짜 Repository) | 조회·등록·수정·삭제, 404 처리, 설명 저장, 수정 시 설명 유지 |
| `ProductControllerTest` | MockMvc + 가짜 Service | 상태 코드, 입력값 검증, 응답 필드 순서, 설명 1000자 허용 / 1001자 거부 |
| `GlobalExceptionHandlerTest` | MockMvc | 오류 응답 형식, 내부 정보 비노출 |
| `ProductApiIntegrationTest` | Spring Boot + H2 | Controller부터 DB까지 실제 저장·수정·삭제 |
| `StubAiDescriptionClientTest` | 단위 테스트 | Stub 설명 문구, 가격 쉼표 형식, 경계값 |
| `ProductDescriptionServiceTest` | Mockito (가짜 Service, 가짜 AI) | 404 시 AI 미호출, AI 실패 502, 한도 초과 503, 지연 504, 빈 응답 502 |
| `ProductDescriptionControllerTest` | MockMvc + 가짜 Service | 상태 코드, 오류 형식, AI 내부 메시지 비노출 |
| `ProductDescriptionIntegrationTest` | Spring Boot + H2 (Stub 모드) | 설명 생성 후 DB가 바뀌지 않았는지 확인 |
| `GeminiAiDescriptionClientTest` | 가짜 Gemini 서버 (localhost) | 요청 형식(모델 경로, 키 헤더, 프롬프트), 응답 파싱, 401/403/5xx·429·타임아웃·연결 실패·잘못된 응답·잘린 응답 구분 |
| `AiClientSelectionTest` | Spring 컨텍스트 | 설정에 따라 Stub/Gemini 중 하나만 등록, 키 없으면 시작 실패 |
| `GeminiDescriptionIntegrationTest` | Spring Boot + H2 + 가짜 Gemini 서버 | Gemini 모드에서 Controller부터 Gemini 호출까지 연결, 오류별 상태 코드 |
| `DescriptionReviewFlowIntegrationTest` | Spring Boot + H2 | 등록 → 초안 생성(저장 안 됨) → 검토한 설명 저장 → 조회, 수정 시 설명 유지 |
| `FlywayMigrationTest` | Spring Boot + H2 | V1·V2 적용, `description` 컬럼(VARCHAR(1000), NULL 허용) |

AI 관련 테스트는 실제 Google 서버 대신 내 PC 안에 띄운 가짜 Gemini 서버를 사용하므로, CI에서도 API 키 없이 실행됩니다.

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
| 7 | POST 성공 시 201 Created 반환, 상품명 최대 255자 검증 (DB 컬럼보다 긴 이름이 500을 내던 문제를 400으로 수정) |
| 8 | AI 상품 설명 생성 API (AI 호출 인터페이스 분리, Stub 구현, 502/504 오류 처리) |
| 9 | Google Gemini API 실제 연동, Stub/Gemini 설정 전환, 429를 503으로 처리, 연결·응답 타임아웃 |
| 10 | AI 설명 검토 후 저장 (`PUT /products/{id}/description`), 상품 응답 DTO(`ProductResponse`) 도입, Flyway로 스키마 관리 전환 |
| 11 | Dockerfile + Docker Compose — 예정 |
| 12 | 배포, AI API 호출 보호, 최종 검증 — 예정 |

> Story 1~6은 Git 도입 전에 진행했으며, 그 결과가 첫 커밋에 포함되어 있습니다.
> 이후 변경 사항은 Story 단위로 기록합니다.

> Story 9는 원래 AWS Bedrock 연동으로 계획했으나, 신규 AWS 계정 확인이 정해 둔 기한까지 끝나지 않아
> Google Gemini API로 먼저 구현했습니다. Story 8에서 AI 호출부를 인터페이스로 분리해 두었기 때문에,
> 기존 Service·Controller는 거의 그대로 두고 구현체만 추가하는 방식으로 대응할 수 있었습니다.

> Story 10에서는 AI가 생성한 설명을 바로 저장하지 않고, 사람이 검토·수정한 뒤 저장하도록 생성(미리보기)과 저장 API를 분리했습니다.
> AI 출력이 그대로 상품 정보가 되지 않게 하기 위한 설계입니다.
