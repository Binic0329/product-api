# CLAUDE.md

이 파일은 이 저장소에서 작업하는 AI 코딩 에이전트(Claude Code)를 위한 작업 지침이다.
사람용 소개와 실행 방법은 README.md를 본다.

## 프로젝트 개요

- 상품을 등록·조회·수정·삭제하는 REST API. 취업용 백엔드 포트폴리오다.
- 기능은 Story 단위로 나눠 개발하고, 설계 결정은 개발자가 직접 내린다. AI는 선택지와 근거를 제시하고, 결정이 필요하면 멈춰서 묻는다.

## 기술 스택 (pom.xml 기준)

- Java 21, Spring Boot 4.0.9, Maven (Maven Wrapper 사용)
- spring-boot-starter-webmvc, spring-boot-starter-validation, spring-boot-starter-data-jpa (Hibernate)
- MySQL 8.4 (mysql-connector-j, runtime)
- Flyway (spring-boot-starter-flyway, flyway-mysql)
- 테스트: spring-boot-starter-webmvc-test (JUnit, Mockito, MockMvc), H2 (test 전용)

## 구조

모든 클래스는 `src/main/java/com/example/product_api` 한 패키지에 있다.

| 파일 | 역할 |
|---|---|
| `ProductController` | HTTP 요청·응답만 담당. `@Valid` 검증, 상태 코드 지정, Entity → `ProductResponse` 변환. 비즈니스 로직을 두지 않는다. 설명 저장 `PUT /products/{id}/description`도 여기 있다 |
| `ProductService` | 비즈니스 로직과 트랜잭션(`@Transactional`). 없는 상품은 `ResponseStatusException(NOT_FOUND)`. `updateDescription`은 설명만 바꾼다 |
| `ProductRepository` | `JpaRepository<Product, Long>`. DB 접근만 담당 |
| `Product` | JPA Entity (`products` 테이블). id는 `@GeneratedValue(IDENTITY)`로 DB가 생성, `setId` 없음. `description`(최대 1000자, null 허용) |
| `ProductRequest` | POST/PUT 요청 DTO. 검증 규칙은 여기에 둔다. description은 받지 않는다 |
| `ProductResponse` | 상품 응답 record `{id, name, price, description}`. 상품 API는 Entity를 그대로 응답하지 않는다 |
| `ProductDescriptionRequest` | 설명 저장 요청 DTO. `@NotBlank`, `@Size(max = 1000)` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 모든 오류를 `ApiErrorResponse` 형식으로 변환 |
| `ApiErrorResponse` | 오류 응답 record `{status, message}` |
| `ProductDescriptionController` | `POST /products/{id}/generate-description`. `ProductController`와 분리해 CRUD 코드·테스트를 건드리지 않는다 |
| `ProductDescriptionService` | 상품 조회(404 재사용) → AI 호출 → AI 예외를 502/503/504로 변환. 느린 AI 호출 중 DB 연결을 잡지 않도록 `@Transactional`을 붙이지 않고 `spring.jpa.open-in-view=false`를 유지한다 |
| `AiDescriptionClient` | AI 호출 인터페이스. 구현체는 HTTP·SDK 예외를 `AiClientException`/`AiUnavailableException`/`AiTimeoutException`으로 바꿔 던진다 |
| `StubAiDescriptionClient` | 실제 호출 없이 고정 문구를 돌려주는 구현체 (Story 8). `ai.client`가 없거나 `stub`일 때 등록 |
| `GeminiAiDescriptionClient` | Gemini `generateContent` REST 호출 (Story 9). `ai.client=gemini`일 때만 등록. `RestClient` + JDK HttpClient 타임아웃, 키는 `x-goog-api-key` 헤더 |
| `AiClientException`, `AiUnavailableException`, `AiTimeoutException` | AI 실패(→502), 요청 한도 초과 429(→503), AI 응답 지연(→504). 뒤의 둘은 `AiClientException`을 상속하므로 Service에서 먼저 잡는다 |
| `ProductDescriptionResponse` | 설명 생성 응답 record `{productId, description}`. DB에 저장하지 않는다 |
| `HelloController` | `GET /hello` 학습용 엔드포인트 (상품 API와 무관) |

- 의존성은 생성자 주입으로 받는다. 직접 `new`로 Service/Repository를 만들지 않는다.
- Controller에는 `Product` Entity가 아닌 `ProductRequest`/`ProductDescriptionRequest`를 요청 본문으로 받고, 응답은 `ProductResponse`로 돌려준다.

## 현재 API 계약 (유지 대상)

| Method | URL | 성공 | 실패 |
|---|---|---|---|
| GET | `/products` | 200 | - |
| GET | `/products/{id}` | 200 | 404 |
| POST | `/products` | 201 | 400 |
| PUT | `/products/{id}` | 200 | 400, 404 |
| DELETE | `/products/{id}` | 204 | 404 |
| POST | `/products/{id}/generate-description` | 200 | 404, 502, 503, 504 |
| PUT | `/products/{id}/description` | 200 | 400, 404 |

상품 응답 (`ProductResponse`): `{"id": 1, "name": "...", "price": 1000, "description": null}`. 필드 순서는 id, name, price, description

설명 검토·저장 (Story 10):
- AI 초안 생성(`generate-description`)은 미리보기 전용이다. DB에 저장하지 않는다
- 사람이 검토한 설명은 `PUT /products/{id}/description`으로만 저장한다. 요청 `{"description": "..."}`, 응답은 수정된 상품(`ProductResponse`)
- `description`: `@NotBlank`(`description must not be blank`), `@Size(max = 1000)`(`description must be at most 1000 characters`), DB 컬럼 varchar(1000)
- `PUT /products/{id}`(이름·가격 수정)는 저장된 설명을 유지한다
- 다시 저장하면 덮어쓴다. 설명 이력은 관리하지 않는다

설명 생성 (`/generate-description`):
- 요청 본문 없음. 응답 `{"productId": 1, "description": "..."}`, DB에 저장하지 않으므로 201이 아닌 200
- 없는 상품이면 AI를 호출하지 않고 404
- `AiClientException`(오류 상태 코드, 연결 실패, 응답 구조 이상, `finishReason`이 있는데 `STOP`이 아님), AI 응답이 `null`/빈 문자열/공백만: 502 `AI service error`
- `AiUnavailableException`(Gemini 429): 503 `AI service unavailable`
- `AiTimeoutException`: 504 `AI service timeout`
- 그 밖의 예외는 502로 감추지 않는다 (기존 규칙대로 500)
- AI 호출 실패 시 임의의 성공 응답(대체 문구)을 만들어 반환하지 않는다
- 프롬프트 규칙(`GeminiAiDescriptionClient.SYSTEM_INSTRUCTION`): 한국어 1~3문장, 제공된 이름·가격만 사용, 없는 사양·브랜드를 지어내지 않음, 본문만 출력

요청 본문 검증 (`ProductRequest`):
- `id`: 보내면 400 (`id must not be provided`). id는 DB가 정하고, PUT은 URL의 id만 사용한다
- `name`: `@NotBlank`, `@Size(max = 255)` (DB 컬럼 varchar(255))
- `price`: `Integer` + `@NotNull`, `@Min(0)` (누락을 0으로 받지 않기 위해 Integer 사용)

오류 응답 형식은 항상 `{"status": 400, "message": "..."}` 이다.
- `@Valid` 실패: 필드 메시지를 정렬해 `", "`로 연결
- JSON 파싱 실패: `Malformed JSON request or missing required field`
- `ResponseStatusException`: 지정한 reason 사용 (예: `Product not found`)
- 예상하지 못한 예외: 500 `Internal server error`, 상세 원인은 서버 로그에만 남긴다
- 응답에 stack trace, 예외 클래스명, 내부 메시지를 넣지 않는다

명시적인 요청 없이 위 상태 코드, 오류 메시지, 응답 형식을 바꾸지 않는다.

## 설정과 실행

- `src/main/resources/application.properties`: MySQL `localhost:3306/product_api`, 계정은 환경 변수 `DB_USERNAME`, `DB_PASSWORD`, `ddl-auto=validate`, `spring.jpa.open-in-view=false`(켜면 AI 대기 중 DB 연결을 점유한다. 테스트 설정도 같은 값)
- `src/main/resources/application-local.properties`: `ddl-auto=validate` (Story 10부터 로컬에서도 Hibernate가 테이블을 바꾸지 않는다)
- `local` 프로필은 pom.xml의 spring-boot-maven-plugin 설정으로 `spring-boot:run`에서만 적용된다. 테스트에는 적용되지 않는다
- `src/test/resources/application.properties`: 테스트는 H2 메모리 DB(`MODE=MySQL`), `ddl-auto=validate`, 테이블은 Flyway가 만든다. 이 파일을 삭제하거나 MySQL로 바꾸지 않는다. 삭제되면 테스트가 실제 MySQL에 연결되어 `deleteAll()`로 실제 데이터를 지울 수 있다

DB 스키마 (Flyway, Story 10):
- 테이블 구조는 `src/main/resources/db/migration`의 SQL 파일로만 바꾼다. `V1__create_products_table.sql`(기존 테이블), `V2__add_product_description.sql`(description 추가)
- **이미 적용된 마이그레이션 파일은 수정하지 않는다.** 변경은 새 버전 파일(`V3__...`)로 추가한다. Entity와 마이그레이션은 같은 작업에서 함께 고친다
- `ddl-auto`를 `update`/`create`/`create-drop`으로 되돌리지 않는다 (Flyway와 충돌)
- `spring.flyway.baseline-on-migrate=false`가 기본값이다. 기록 테이블 없는 기존 MySQL을 전환할 때만 사용자가 백업 후 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`로 한 번 실행한다 (README "기존 DB를 Flyway로 전환")
- 실제 MySQL에 마이그레이션을 적용하는 것은 "위험한 작업"이다. AI가 실행하지 않고, 사용자가 백업과 승인 후 직접 한다
- AI 설정 (`application.properties`): `ai.client=${AI_CLIENT:stub}`, `ai.gemini.*`(base-url, model, connect-timeout, read-timeout), `ai.gemini.api-key=${GEMINI_API_KEY:}`
  - 기본값은 Stub. Gemini 모드인데 키가 비어 있으면 서버 시작 시 `GEMINI_API_KEY is not set`으로 실패한다
  - 테스트 설정 파일에는 `ai.*`가 없어서 테스트는 기본적으로 Stub 모드다. Gemini 테스트는 `FakeGeminiServer`(localhost)를 쓰고 실제 Google 서버를 호출하지 않는다
  - 실제 Gemini 호출 확인은 사용자가 환경 변수를 직접 설정해 수동으로 한다. AI는 키 값을 요청·출력하지 않는다

명령어 (Windows PowerShell 기준):
- 서버 실행: `.\mvnw.cmd spring-boot:run`
- 전체 테스트: `.\mvnw.cmd test`
- 확인용 서버를 띄울 때는 사용자의 서버와 겹치지 않도록 다른 포트를 쓰고, 확인 후 반드시 종료한다: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"`

CI (GitHub Actions):
- 설정 파일: `.github/workflows/ci.yml`. `main` push와 `main` 대상 PR에서 Java 21로 `./mvnw -B test` 실행
- 위 H2 테스트 설정으로만 돌아간다. CI에 MySQL, DB 계정 환경 변수, `secrets`를 추가하지 않는다
- 권한은 `contents: read`만 둔다. 늘려야 하면 먼저 이유를 설명하고 확인을 받는다
- `mvnw`는 Git에 실행 권한(`100755`)으로 기록되어 있다. 이 권한이 빠지면 CI가 실패한다

## 테스트

테스트 클래스별 역할:

| 테스트 | 방식 |
|---|---|
| `ProductServiceTest` | Mockito로 가짜 `ProductRepository` 주입, 비즈니스 규칙 검증 |
| `ProductControllerTest` | MockMvc standaloneSetup + 가짜 `ProductService`. 상태 코드, 검증, 잘못된 요청이 Service에 닿지 않는지(`verifyNoInteractions`) |
| `GlobalExceptionHandlerTest` | 오류 응답 형식, 500 시 내부 정보 비노출 (테스트 전용 `FailingController` 사용) |
| `ProductApiIntegrationTest` | `@SpringBootTest` + H2. Controller부터 DB까지 실제 저장·수정·삭제 확인 |
| `ProductApiApplicationTests` | 컨텍스트 로딩 |
| `StubAiDescriptionClientTest` | Stub 문구, `Locale.KOREA` 천 단위 쉼표, 0원·255자 경계값 |
| `ProductDescriptionServiceTest` | Mockito로 가짜 `ProductService`·`AiDescriptionClient` 주입. 404 시 AI 미호출, 502/503/504 변환, 빈 응답 502 |
| `ProductDescriptionControllerTest` | MockMvc standaloneSetup. 상태 코드와 오류 형식, AI 내부 메시지 비노출 |
| `ProductDescriptionIntegrationTest` | `@SpringBootTest` + H2 + 실제 Stub. 응답 확인 후 Repository로 DB가 바뀌지 않았는지 확인 |
| `GeminiAiDescriptionClientTest` | `FakeGeminiServer`(JDK `HttpServer`, localhost)에 실제 HTTP 요청. 요청 형식, 파싱, 상태 코드별 예외, 타임아웃, 연결 실패, 잘린 응답 |
| `AiClientSelectionTest` | `ApplicationContextRunner`. `ai.client` 값에 따라 구현체가 하나만 등록되는지, 키 없으면 시작 실패 |
| `GeminiDescriptionIntegrationTest` | `@SpringBootTest` + H2 + `ai.client=gemini` + `FakeGeminiServer`. 전체 흐름과 오류별 상태 코드 |
| `DescriptionReviewFlowIntegrationTest` | `@SpringBootTest` + H2. 등록 → 초안 생성(저장 안 됨) → 설명 저장 → 조회, 수정 시 설명 유지, 1000/1001자. Repository로 DB 확인 |
| `FlywayMigrationTest` | `@SpringBootTest` + H2. V1·V2 적용 여부, `description` 컬럼 길이와 NULL 허용 |

규칙:
- 테스트 개수는 이 문서에 고정하지 않는다. 작업 전 `.\mvnw.cmd test`로 현재 결과(실행 개수, 실패 개수)를 기준으로 확인한다.
- 코드를 바꾸면 관련 테스트를 실행하고, 마지막에 전체 테스트를 실행해 결과를 그대로 보고한다. 작업 전 기준과 비교해 늘거나 줄어든 테스트와 그 이유를 함께 적는다.
- 기능 추가·수정 시 정상, 예외, 경계값(예: 255자 허용 / 256자 거부)을 모두 테스트한다.
- 버그를 고칠 때는 먼저 실패하는 테스트로 재현한 뒤 수정한다.
- 테스트에서 실제 MySQL이나 외부 서비스를 호출하지 않는다. H2와 Mockito를 사용한다.
- DB 반영 여부는 응답만으로 판단하지 않는다. 통합 테스트에서 Repository로 다시 조회해 확인한다.
- 테스트를 통과시키기 위해 기존 테스트의 기대값을 임의로 바꾸지 않는다. API 계약이 바뀌는 경우에만 바꾸고, 그 사실을 보고한다.

## 문서 갱신

API 동작(엔드포인트, 상태 코드, 요청 규칙, 오류 메시지)이 바뀌면 같은 작업 안에서 함께 고친다.
- `api.http`: 정상 요청과 오류 확인 요청. 기존 MySQL 데이터를 지우는 요청을 넣지 않는다 (삭제 요청은 같은 파일에서 새로 만든 상품만 대상으로)
- `README.md`: API 표, 요청 규칙, 테스트 개수 등. 숫자가 여러 곳에 있으니 모두 확인한다

## 민감 정보

- 비밀번호, API 키, 토큰을 코드, 설정 파일, 테스트, 문서, 커밋에 넣지 않는다. 환경 변수(`${...}`)로만 참조한다.
- 환경 변수 값을 출력하거나 로그에 남기지 않는다. 확인이 필요하면 설정 여부(true/false)만 확인한다.
- 사용자에게 비밀번호나 키를 채팅으로 요청하지 않는다. 설정 방법만 안내한다.
- 저장소는 Public이다. 커밋 전 `git status`로 의도하지 않은 파일이 없는지 확인한다.

## Git 규칙

- `main`에서 직접 작업하지 않는다. 작업 전 현재 브랜치를 확인하고, `main`이면 멈추고 알린다.
- 브랜치 이름: `종류/story-번호-설명` 또는 `종류/설명` (소문자, 하이픈). 종류: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`
- 커밋 제목: Conventional Commit 형식 `종류: 한국어 요약` (예: `feat: POST 201 응답, 상품명 길이 검증 추가`)
- 커밋 본문: 자연스러운 한국어("~함", "~예정"), 핵심 변경을 목록으로. 교과서 같은 문체는 피한다
- `Co-Authored-By` 문구를 추가하지 않는다
- 커밋, push, PR 생성, 병합은 사용자가 요청했을 때만 한다
- 이미 push한 커밋을 amend, rebase, force push로 고치지 않는다. 추가 커밋이나 별도 PR로 수정한다

## 위험한 작업 (실행 전 사용자 승인 필수)

아래 작업은 되돌리기 어렵다. 실행하기 전에 무엇을, 왜, 어떤 영향이 있는지 설명하고 사용자의 명시적인 승인을 받는다. 다른 작업의 승인을 이 작업의 승인으로 간주하지 않는다.

- 실제 MySQL 데이터 삭제: `DELETE`, `TRUNCATE`, 실제 DB에 연결된 상태의 `deleteAll()` 등
- 스키마 초기화·삭제·변경: `DROP`, MySQL 대상 `ddl-auto`를 `create`/`create-drop`으로 바꾸는 것, 테이블·컬럼 구조를 직접 바꾸는 SQL
- 강제 push(`git push --force` 등)와 기록 변경: push된 커밋의 amend, rebase, `git reset --hard`
- 대량 파일 삭제, 폴더 삭제, 원격 브랜치 삭제
- Windows 환경 변수, 전역 Git 설정 등 프로젝트 밖의 설정 변경

## 작업 방식

- 요청 범위 밖의 파일은 수정하지 않는다. 필요해 보이면 먼저 이유를 설명하고 확인을 받는다.
- 요청하지 않은 대규모 리팩터링, 새 라이브러리 도입, 구조 변경을 하지 않는다.
- 구현 중 기존 코드와 충돌하거나 동작이 바뀌는 문제를 발견하면, 임의로 우회하지 말고 멈춰서 원인과 선택지를 보고한다.
- 큰 작업은 작은 단계로 나누고, 단계마다 결과를 확인한다.
- 실제 MySQL의 기존 데이터를 삭제하지 않는다. 꼭 필요하면 위 "위험한 작업" 절차에 따라 승인을 받는다. 확인용 데이터를 만들었다면 보고한다.
- 사용자는 초보 개발자다. 설명은 짧고 쉽게, 실제 파일명과 코드를 근거로 한다. 추측은 추측이라고 밝힌다.

## 작업 완료 보고

작업이 끝나면 다음을 보고한다.
1. 변경한 파일과 변경 이유
2. 핵심 코드
3. 테스트 결과 (실행한 명령, 전체 실행 결과의 통과/실패 개수, 작업 전 기준과 비교)
4. 사용자가 직접 확인할 수 있는 방법 (api.http 요청 등)
5. 남은 위험 요소와 다음에 할 일

## 개발 보고서

이 섹션은 Story를 이해하는 데 도움이 되는 보고서의 양식만 정한다. 작업 순서, 승인, 커밋·push·PR은 위의 기존 지침을 그대로 따른다.

### 언제, 무엇을
- Story를 완료하면 작성한다. `fix`, `docs`, `chore` 작업과 지난 Story는 사용자가 요청할 때 작성한다.
- 산출물은 3개다.
  - 코드 리뷰 보고서: `docs/reports/story-NN/code-review.md`
  - 학습 보고서: `docs/reports/story-NN/learning.md`
  - 두 보고서의 핵심을 합친 읽기용 PDF 1개. Markdown을 그대로 변환하지 않고 중복을 줄여 편집한다
- PDF를 저장소에 커밋할지는 사용자 지시를 따른다. 지시가 없으면 커밋하지 않는다.
- PDF 생성 도구는 `pom.xml`에 추가하지 않는다. 생성 스크립트와 중간 파일은 저장소 밖 임시 폴더에 둔다.

### 독자와 문체
- 한국어 존댓말로 쓴다.
- 이 프로젝트를 처음 보는 개발 학습자가 읽어도 이해할 수 있게 쓴다. 대화 내용을 알아야 이해되는 표현은 쓰지 않는다.
- 보고서 앞부분에 필요한 용어를 짧게 정리하고, 전문 용어는 처음 나올 때 풀이한다.
- "본 설명"과 "이해를 돕는 해설"을 함께 쓴다.
  - 본 설명: 구현 내용과 기술적 의미
  - 해설: 구체적인 예시나, 필요하면 비유. 작은 회색 글씨로 숨기지 않는다
  - 본 설명을 짧게 반복하기만 하는 해설은 쓰지 않는다.

### 설명 순서
1. 무엇을 하는 작업인지 구체적인 행동과 예시로 먼저 설명한다. (예: "Wireless Mouse, 25,000원 상품의 설명을 AI로 만들고, 사람이 고친 문장만 저장한다")
2. 실제 요청이 어떤 과정을 거쳐 어떤 결과가 되는지 보여 준다. (예: `api.http` 요청 → Controller → Service → Repository → MySQL → 응답 JSON)
3. 핵심 코드와 중요한 줄의 역할과 문법을 설명한다.
4. 검증 한계와 사용자가 해야 할 일을 안내한다.

### 코드 리뷰 보고서
- 다음을 포함한다.
  - 작업 목표와 구현 범위
  - 수용 기준별 충족 여부와 근거
  - 변경 파일과 목적
  - 실제 실행과 데이터 흐름
  - 설계 결정, 선택 이유와 대안 (관련 ADR이 있으면 `docs/adr`를 참조)
  - 자체 리뷰 발견 사항
  - 검증 결과
  - 놓치기 쉬운 부분과 후속 조치
- 발견 사항과 검사 결과를 구분한다. 발견 사항은 심각도 / 위치 / 문제 / 영향 / 제안으로 정리한다.
- 확정한 결함이 없으면 검토 범위와 함께 "확정한 결함 없음"이라고 쓴다.
- 자체 검토를 독립 리뷰로 표현하지 않는다.

### 학습 보고서와 핵심 코드 읽기
- 전체 코드 대신 중요한 클래스, 메서드, 설정, 마이그레이션 SQL과 그 핵심 줄을 고른다.
- 코드 위에 저장소 상대 경로와 정확한 줄 범위를 쓴다. (예: `src/main/java/com/example/product_api/ProductService.java:40–45`)
- 원문인지, 일부 발췌인지, 줄바꿈만 다시 편집했는지 표시한다.
- 코드를 설명하기 전에 전체 역할, 호출 위치, 입력과 출력을 먼저 설명한다.
- 중요한 줄은 "역할 → 빠지거나 바뀌면 생기는 차이 → 이를 구현하는 문법" 순서로 설명한다.
- 이 프로젝트에서 자주 나오는 문법을 실제 코드 가까이에서 설명한다: 어노테이션(`@Transactional`, `@Valid` 등), 생성자 주입, 제네릭(`JpaRepository<Product, Long>`), `record`, 람다와 스트림, `Optional`, 예외 상속과 `catch` 순서, 텍스트 블록(`"""`)
- Java 언어 문법, JDK 표준 API, Spring·JPA·Flyway 같은 라이브러리 API를 구분해서 설명한다.
- 개념 설명만으로 코드의 문법 설명을 대신하지 않는다.
- 이해도 확인 질문 3개와 해설을 넣는다.

### 성공과 실패 사례
- "실제 입력 → 처리 단계 → 실제 결과 → 기대값과 비교" 순서로 설명한다.
- 가능하면 실제 상품명, 가격, 상태 코드, 응답 JSON 같은 구체적인 값을 쓴다.
- 성공 사례뿐 아니라 잘못된 입력과 실패 사례(400, 404, 502 등)도 보여 준다.
- 직접 실행한 결과와 설명용 예측을 명확히 구분한다.
- 의도한 오류를 올바르게 거부해서 테스트가 통과한 경우, 그것이 정상 동작임을 설명한다.

### 검증의 정확성
- 실행 시점, 환경, 명령, 결과를 필요한 만큼 기록한다. (예: Windows PowerShell, `.\mvnw.cmd test`)
- 출처를 구분한다.
  - 로컬 실행 결과: AI가 직접 실행한 것
  - 원격 CI 결과: `gh`로 직접 확인했으면 그렇게, 사용자가 알려 줬으면 "사용자 제공"
  - 실제 MySQL·Gemini 수동 확인: 사용자가 한 것이므로 "사용자 제공"
  - 과거 기록: Git 기록·README·ADR에서 가져온 것
  - 미검증 사항
- 테스트 실행·통과·실패·건너뜀 수를 정확하게 쓴다. 실행하지 않은 것을 통과로 쓰지 않는다.
- MockMvc 테스트나 HTTP 응답 확인을 브라우저·실제 서버 테스트 성공으로 표현하지 않는다.
- 경고와 취약점에는 조치가 필요한지와 그 이유를 붙인다. 영향 분석이나 수정 보류를 "해결"로 쓰지 않는다.

### 지난 Story 보고서 (소급 작성)
- 근거는 Git 기록, 해당 커밋 시점의 코드, README, ADR이다. 기억이나 추측으로 채우지 않는다.
- 당시 코드가 지금과 다르면 당시 커밋의 코드를 인용하고 그 사실을 표시한다. 지금 코드를 당시 코드처럼 쓰지 않는다.
- Story 1~6은 Git 도입 전 작업이라 첫 커밋 하나에 섞여 있다. Story별 변경 범위와 당시 실행 결과를 확인할 수 없다는 점을 밝힌다.
- 다시 실행할 수 없는 당시 결과는 "과거 기록"으로 표시한다. 보고서를 쓰면서 지금 다시 실행한 결과는 실행 날짜와 함께 따로 쓴다.
- 보고서 작성 날짜와 Story 진행 시점을 구분해 쓴다. 날짜를 과거로 맞추지 않는다.

### PDF 구성
- 첫 페이지에서 작업의 의미, 완료 상태, 핵심 검증 결과를 보여 준다.
- 이어서 핵심 구조, 코드, 성공과 실패 사례를 자연스럽게 연결한다.
- 표지, 목차, 억지 페이지 나눔을 넣지 않는다. 분량은 작업 규모에 맞추고 여백을 채우려고 내용을 늘리지 않는다.
- 실행 흐름이나 조건 분기(예: 502/503/504로 나뉘는 과정)는 그림으로 보여 준다.
- 제목, 본문, 표, 코드를 분명히 구분하고, 본문은 읽기 편한 크기로 한다.
- 코드와 해당 해설은 가깝게 둔다. 코드와 명령어는 복사할 수 있는 고정폭 글꼴로 쓴다.

### 민감 정보와 불필요한 식별자
- 위 "민감 정보" 규칙을 보고서와 PDF에도 똑같이 적용한다. `DB_PASSWORD`, `GEMINI_API_KEY` 같은 실제 값, DB 접속 정보, 개인정보를 넣지 않는다.
- 사용자 PC 경로(예: `C:\Users\...`, OneDrive 폴더 경로)를 넣지 않는다. 코드 위치는 저장소 상대 경로와 줄 번호로만 쓴다.
- 로그는 민감한 값을 지운 뒤 인용한다.
- PDF에는 커밋 해시, PR 번호와 링크, 내부 서버 주소, PC 경로를 넣지 않는다. Markdown 보고서에는 출처 확인용으로 넣어도 된다.
- 패키지 정확한 버전과 취약점 ID는 설명에 필요할 때만 쓴다.
- PDF 메타데이터(작성자, 생성 도구 등)에 개인정보나 경로가 없는지 확인한다.

### PDF를 전달하기 전에 확인
- 렌더링해서 한글이 빠지지 않았는지, 표와 코드가 잘리지 않았는지, 페이지 나눔이 어색하지 않은지 확인한다.
- 텍스트를 추출해서 코드와 명령어가 그대로 복사되는지 확인한다. 특히 ASCII 따옴표(`"`, `'`)와 하이픈(`-`)이 다른 문자로 바뀌지 않았는지 본다.
- 보고서 내용이 실제 코드, 테스트 결과와 일치하는지 대조한다.
- PDF 생성에 실패했거나 확인하지 못한 항목은 숨기지 않고 알린다.
