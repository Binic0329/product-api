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
- 테스트: spring-boot-starter-webmvc-test (JUnit, Mockito, MockMvc), H2 (test 전용)

## 구조

모든 클래스는 `src/main/java/com/example/product_api` 한 패키지에 있다.

| 파일 | 역할 |
|---|---|
| `ProductController` | HTTP 요청·응답만 담당. `@Valid` 검증, 상태 코드 지정. 비즈니스 로직을 두지 않는다 |
| `ProductService` | 비즈니스 로직과 트랜잭션(`@Transactional`). 없는 상품은 `ResponseStatusException(NOT_FOUND)` |
| `ProductRepository` | `JpaRepository<Product, Long>`. DB 접근만 담당 |
| `Product` | JPA Entity (`products` 테이블). id는 `@GeneratedValue(IDENTITY)`로 DB가 생성, `setId` 없음 |
| `ProductRequest` | POST/PUT 요청 DTO. 검증 규칙은 여기에 둔다 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`. 모든 오류를 `ApiErrorResponse` 형식으로 변환 |
| `ApiErrorResponse` | 오류 응답 record `{status, message}` |
| `ProductDescriptionController` | `POST /products/{id}/generate-description`. `ProductController`와 분리해 CRUD 코드·테스트를 건드리지 않는다 |
| `ProductDescriptionService` | 상품 조회(404 재사용) → AI 호출 → AI 예외를 502/504로 변환. 느린 AI 호출 중 트랜잭션을 잡지 않도록 `@Transactional`을 붙이지 않는다 |
| `AiDescriptionClient` | AI 호출 인터페이스. 구현체는 SDK 예외를 `AiClientException`/`AiTimeoutException`으로 바꿔 던진다 |
| `StubAiDescriptionClient` | 실제 호출 없이 고정 문구를 돌려주는 구현체 (Story 8). 실제 모델 연동은 Story 9 |
| `AiClientException`, `AiTimeoutException` | AI 실패(→502), AI 응답 지연(→504). `AiTimeoutException`은 `AiClientException`을 상속 |
| `ProductDescriptionResponse` | 설명 생성 응답 record `{productId, description}`. DB에 저장하지 않는다 |
| `HelloController` | `GET /hello` 학습용 엔드포인트 (상품 API와 무관) |

- 의존성은 생성자 주입으로 받는다. 직접 `new`로 Service/Repository를 만들지 않는다.
- Controller에는 `Product` Entity가 아닌 `ProductRequest`를 요청 본문으로 받는다.

## 현재 API 계약 (유지 대상)

| Method | URL | 성공 | 실패 |
|---|---|---|---|
| GET | `/products` | 200 | - |
| GET | `/products/{id}` | 200 | 404 |
| POST | `/products` | 201 | 400 |
| PUT | `/products/{id}` | 200 | 400, 404 |
| DELETE | `/products/{id}` | 204 | 404 |
| POST | `/products/{id}/generate-description` | 200 | 404, 502, 504 |

설명 생성 (`/generate-description`):
- 요청 본문 없음. 응답 `{"productId": 1, "description": "..."}`, DB에 저장하지 않으므로 201이 아닌 200
- 없는 상품이면 AI를 호출하지 않고 404
- `AiClientException`, AI 응답이 `null`/빈 문자열/공백만: 502 `AI service error`
- `AiTimeoutException`: 504 `AI service timeout`
- 그 밖의 예외는 502로 감추지 않는다 (기존 규칙대로 500)
- 현재는 `StubAiDescriptionClient`만 있고 테스트에서도 실제 AI를 호출하지 않는다

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

- `src/main/resources/application.properties`: MySQL `localhost:3306/product_api`, 계정은 환경 변수 `DB_USERNAME`, `DB_PASSWORD`, `ddl-auto=none`
- `src/main/resources/application-local.properties`: `ddl-auto=update` (로컬 개발 전용)
- `local` 프로필은 pom.xml의 spring-boot-maven-plugin 설정으로 `spring-boot:run`에서만 적용된다. 테스트에는 적용되지 않는다
- `src/test/resources/application.properties`: 테스트는 H2 메모리 DB(`MODE=MySQL`), `ddl-auto=create-drop`. 이 파일을 삭제하거나 MySQL로 바꾸지 않는다. 삭제되면 테스트가 실제 MySQL에 연결되어 `deleteAll()`로 실제 데이터를 지울 수 있다

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
| `ProductDescriptionServiceTest` | Mockito로 가짜 `ProductService`·`AiDescriptionClient` 주입. 404 시 AI 미호출, 502/504 변환, 빈 응답 502 |
| `ProductDescriptionControllerTest` | MockMvc standaloneSetup. 상태 코드와 오류 형식, AI 내부 메시지 비노출 |
| `ProductDescriptionIntegrationTest` | `@SpringBootTest` + H2 + 실제 Stub. 응답 확인 후 Repository로 DB가 바뀌지 않았는지 확인 |

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
