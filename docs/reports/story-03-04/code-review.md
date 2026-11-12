# Story 3~4 코드 리뷰 보고서 (소급 작성)

- 대상: Story 3 POST/PUT 입력값 검증(Bean Validation), Story 4 전역 예외 처리와 오류 응답 형식 통일
- 보고서 작성일: 2026-11-12
- Story 진행 시점: Git 도입 전. 첫 커밋(`72fcc5d`, 2026-10-26) 이전이며 정확한 날짜는 기록이 없습니다.
- 작성 방식: 작성자(AI)의 **자체 검토**입니다. 독립 리뷰가 아닙니다.

## 0. 먼저 알아 둘 한계

- Story 1~6은 첫 커밋 하나에 합쳐져 있어 **Story 3~4 당시의 코드와 실행 결과는 남아 있지 않습니다.** 인용한 코드는 Story 6까지 반영된 첫 커밋 이후의 코드입니다.
- 첫 커밋 이후 이 범위에서 바뀐 것은 아래 3가지뿐입니다(`git diff 72fcc5d HEAD`로 확인).
  - Story 7(`f3ddc83`, 2026-10-28): `ProductRequest.name`에 `@Size(max = 255)` 추가. Story 3 범위가 아니므로 이 보고서에서는 "이후 추가"로만 표시합니다.
  - fix(`3188515`, 2026-11-09): `GlobalExceptionHandler` 주석 2줄 수정(동작 변경 없음).
- 첫 커밋의 `GlobalExceptionHandler` 주석에 "int 필드(id) 누락"이라는 표현이 있었습니다. 당시 id가 `int`였던 흔적으로 보이지만, Story 3~4 시점의 정확한 필드 구성은 확인할 수 없습니다.

## 1. 용어

| 용어 | 뜻 |
|---|---|
| Bean Validation | 필드에 `@NotBlank`, `@Min` 같은 규칙을 붙여 값을 자동 검사하는 Java 표준(Jakarta Validation) |
| `@Valid` | "이 요청 본문을 검사하라"는 표시. 검사 실패 시 `MethodArgumentNotValidException`이 납니다 |
| 전역 예외 처리 | 여러 Controller에서 난 예외를 한 클래스(`@RestControllerAdvice`)에서 모아 응답으로 바꾸는 방식 |
| 오류 응답 형식 | 이 프로젝트의 모든 오류 본문 `{"status": 400, "message": "..."}` |
| 400 / 500 | 클라이언트 요청이 잘못됨 / 서버 내부 오류 |

## 2. 작업 목표와 구현 범위

**Story 3** — POST/PUT 요청 본문을 서버에 들어오기 전에 검사합니다.
- 예: `{"name": "   ", "price": 30000}` → 400 `name must not be blank`. Service와 DB에는 닿지 않습니다.
- 규칙(현재): `id`는 보내면 안 됨(`@Null`), `name`은 공백 불가(`@NotBlank`), `price`는 필수(`@NotNull`)이고 0 이상(`@Min(0)`). `name` 최대 255자(`@Size`)는 Story 7에서 추가.

**Story 4** — 어떤 오류든 같은 JSON 형식으로 돌려줍니다.
- 검증 실패 → 400, 필드 메시지를 정렬해 `", "`로 연결
- 깨진 JSON → 400 `Malformed JSON request or missing required field`
- `ResponseStatusException`(예: 404) → 지정한 reason
- 예상하지 못한 예외 → 500 `Internal server error`, 원인은 서버 로그에만

## 3. 수용 기준별 충족 여부

원래 수용 기준 문서는 남아 있지 않습니다. README의 Story 표와 현재 API 계약(CLAUDE.md)에서 정리했습니다.

| 기준 | 충족 | 근거 |
|---|---|---|
| 잘못된 POST 본문(이름 누락·빈 값·공백, 가격 음수·누락)은 400 | 충족 | `ProductControllerTest.createProductReturnsBadRequestForInvalidInput` (`ProductControllerTest.java:90–109`) |
| 잘못된 PUT 본문도 400 | 충족 | `ProductControllerTest.updateProductReturnsBadRequestForInvalidInput` |
| 검증 실패 요청은 Service·DB에 닿지 않음 | 충족 | 위 두 테스트의 `verifyNoInteractions(productService)`, `ProductApiIntegrationTest.invalidRequestDoesNotSaveAnything`(DB 개수 0) |
| 가격 0원은 허용(경계값) | 충족 | `ProductControllerTest.createProductAllowsZeroPrice` → 201 |
| 오류 본문이 `{status, message}` | 충족 | `GlobalExceptionHandlerTest` 6개 테스트 |
| 여러 필드 오류는 정렬 후 `", "`로 연결 | 충족 | `GlobalExceptionHandlerTest.multipleValidationErrorsAreJoined` |
| 깨진 JSON은 400, 내부 라이브러리 이름 노출 안 함 | 충족 | `malformedJsonReturnsBadRequestWithoutInternalDetails` — 본문에 `jackson` 없음 |
| 500 응답에 예외 메시지·클래스명 노출 안 함 | 충족 | `unexpectedErrorHidesInternalDetails` — `secret internal detail`, `IllegalStateException` 없음 |
| 각 필드 메시지 문구가 정확함 | 일부 | `price must not be null` 문구를 확인하는 테스트 없음 (발견 사항 F1) |

## 4. 관련 파일과 역할 (현재 기준)

| 파일 | 역할 |
|---|---|
| `src/main/java/com/example/product_api/ProductRequest.java` | 검증 규칙(`@Null`, `@NotBlank`, `@NotNull`, `@Min`, 이후 `@Size`) |
| `src/main/java/com/example/product_api/ProductController.java` | `@Valid @RequestBody ProductRequest`로 검증 실행 (43, 50줄) |
| `src/main/java/com/example/product_api/GlobalExceptionHandler.java` | 예외 → `ApiErrorResponse` 변환, 500 로그 |
| `src/main/java/com/example/product_api/ApiErrorResponse.java` | 오류 응답 record `{status, message}` |
| `src/test/java/.../GlobalExceptionHandlerTest.java` | 오류 형식, 메시지 연결, 내부 정보 비노출 |
| `src/test/java/.../ProductControllerTest.java` | 잘못된 본문 400, Service 미호출 |
| `api.http` | `[400-1]`~`[400-10]` 수동 확인 요청 |

## 5. 실행과 데이터 흐름

```
POST /products  {"name":"   ","price":30000}
  → Jackson이 JSON을 ProductRequest로 변환
      실패(깨진 JSON) → HttpMessageNotReadableException → handleHttpMessageNotReadable → 400 "Malformed JSON ..."
  → @Valid 검사
      실패 → MethodArgumentNotValidException → handleMethodArgumentNotValid → 400 "name must not be blank"
  → (통과 시) ProductService.create → DB 저장 → 201

Service에서 ResponseStatusException(404) → handleExceptionInternal → 404 "Product not found"
그 밖의 예외(예: IllegalStateException) → handleUnexpected → 로그 기록 + 500 "Internal server error"
```

## 6. 설계 결정과 대안

| 결정 | 이유 | 대안 |
|---|---|---|
| 검증 규칙을 요청 DTO에 어노테이션으로 둠 | 규칙이 한곳에 모이고 Controller·Service 코드가 짧아짐 | Service에서 `if`로 직접 검사. 유연하지만 규칙이 흩어짐 |
| `price`를 `int`가 아닌 `Integer` + `@NotNull` | `int`는 값이 빠지면 0이 되어 `@Min(0)`을 통과함. 누락을 잡으려면 `null`이 가능해야 함 (코드 주석 근거) | `int` 유지. 가격 누락이 0원 상품으로 저장됨 |
| `ResponseEntityExceptionHandler` 상속 | Spring 표준 예외(405, 415, 타입 불일치 등)도 원래 상태 코드를 유지하며 같은 형식으로 응답 (코드 주석 근거) | `@ExceptionHandler`만 나열. 놓친 예외는 Spring 기본 형식으로 나감 |
| 여러 오류 메시지를 정렬해 한 문자열로 | 순서가 매번 같아 테스트·클라이언트가 예측 가능 | 필드별 배열 `{"errors": [...]}`. 정보는 많지만 형식이 둘로 갈림 |
| 500은 고정 문구, 원인은 로그에만 | 내부 구조·라이브러리 정보 노출 방지 | 예외 메시지 그대로 응답. 디버깅은 쉽지만 보안상 위험 |

관련 ADR은 없습니다. 이유는 코드 주석과 CLAUDE.md 계약에서 가져왔고, 대안 비교는 작성자가 덧붙였습니다.

README의 "설계 결정" 표에 "POST 본문에 id가 포함된 경우 → 400으로 거절(AI 추천은 무시하고 저장)" 기록이 있습니다. 어느 Story에서 내린 결정인지는 기록이 없습니다. 같은 README 예시 문장("Entity·Service는 아직 수정하지 말 것")으로 보아 Story 6 전환 과정이었을 가능성이 있지만 추측입니다.

## 7. 자체 리뷰 발견 사항

검토 범위: `ProductRequest` 검증 규칙, `ProductController`의 `@Valid` 사용, `GlobalExceptionHandler` 전체, `ApiErrorResponse`, `GlobalExceptionHandlerTest`, `ProductControllerTest`의 검증 테스트, `api.http` 400 요청.

**확정한 결함 없음.** 아래는 개선 제안과 참고 사항입니다.

| ID | 심각도 | 위치 | 문제 | 영향 | 제안 |
|---|---|---|---|---|---|
| F1 | 낮음 | `src/test/java/com/example/product_api/ProductControllerTest.java:90–109` | 잘못된 본문 5종을 상태 코드(400)로만 확인. `price must not be null` 문구는 어느 테스트도 확인하지 않음 | 메시지 문구가 바뀌어도 테스트가 못 잡음. 계약상 문구 변경은 금지 사항 | 별도 `test` 작업에서 `price` 누락 시 `jsonPath("$.message")` 확인 추가 |
| F2 | 참고 | `src/main/java/com/example/product_api/GlobalExceptionHandler.java:49` | 메시지의 "missing required field"에 해당하는 경우가 현재는 거의 없음. `ProductRequest` 필드가 모두 참조 타입(`Long`, `String`, `Integer`)이라 필드 누락은 `null` → 검증 오류로 감 (코드 근거 추론, 실행 확인 안 함) | 클라이언트가 문구를 보고 "필드 누락"으로 오해할 수 있음 | 문구는 API 계약이므로 바꾸지 않음. 바꾸려면 계약 변경으로 별도 결정 필요 |

## 8. 검증 결과

| 출처 | 내용 |
|---|---|
| 로컬 실행 (AI, 2026-11-12) | Windows PowerShell, `.\mvnw.cmd -q test`, 종료 코드 0. surefire 보고서 집계: **실행 108, 실패 0, 오류 0, 건너뜀 0**. Story 1~2 보고서 작성 때 실행한 결과이며, 그 뒤 코드 변경은 없습니다 |
| 위 실행 중 이 범위 | `GlobalExceptionHandlerTest` 6개, `ProductControllerTest`의 검증 관련 테스트(`createProductRejectsBodyWithId`, `createProductReturnsBadRequestForInvalidInput`, `createProductAllowsZeroPrice`, `updateProductReturnsBadRequestForInvalidInput`), `ProductApiIntegrationTest.invalidRequestDoesNotSaveAnything` 모두 통과 |
| 과거 기록 | 첫 커밋 README "자동화 테스트 실행 (현재 29개)". Story 3~4 시점 결과는 기록 없음 |
| 미검증 | 실제 서버에 `api.http`의 `[400-1]`~`[400-10]` 요청. 이번에 실행하지 않음 |

108개는 현재 전체 테스트 수입니다. 이번 작업은 보고서만 추가했으므로 테스트 수는 작업 전과 같습니다. MockMvc 테스트는 실제 서버를 띄운 확인이 아닙니다.

## 9. 놓치기 쉬운 부분과 후속 조치

- **메시지 정렬은 알파벳순**입니다. 필드 순서(id, name, price)가 아니라 메시지 문자열 순서입니다. 지금은 우연히 같아 보이지만, 새 필드가 생기면 순서가 달라질 수 있습니다.
- 한 필드에 규칙이 둘 다 걸리면(예: 공백만 256자인 이름 → `@NotBlank`와 `@Size` 모두 실패) 그 필드의 메시지 두 개가 함께 나옵니다(코드 근거 추론, 실행 확인 안 함).
- 500 응답은 내용을 숨기지만 서버 로그에는 전체 원인이 남습니다(`log.error`). 로그에 민감한 값이 들어가지 않도록 예외 메시지 작성에 주의해야 합니다.
- 사용자 확인 방법: 서버 실행 후 `api.http`의 `[400-1]`~`[400-10]`을 실행하고 각 요청 제목의 메시지와 응답을 비교합니다. 400 요청은 데이터를 바꾸지 않습니다.
- 후속: F1 테스트 보강은 필요하면 별도 `test/` 브랜치에서 진행.
