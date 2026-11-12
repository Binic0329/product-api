# Story 3~4 학습 보고서 (소급 작성)

- 대상: Story 3 POST/PUT 입력값 검증, Story 4 전역 예외 처리와 오류 응답 형식 통일
- 보고서 작성일: 2026-11-12 / Story 진행 시점: Git 도입 전(첫 커밋 2026-10-26 이전, 정확한 날짜 기록 없음)
- 인용 코드: 현재 코드입니다. 첫 커밋 이후 바뀐 부분은 `name`의 `@Size(max = 255)`(Story 7에서 추가)와 주석 2줄뿐이며, 해당 위치에 표시했습니다. **Story 3~4 당시의 코드는 Git에 없습니다.** 줄 번호는 현재 파일 기준입니다.

## 1. 용어 정리

| 용어 | 풀이 |
|---|---|
| 검증(Validation) | 요청 값이 규칙에 맞는지 서버가 처리 전에 확인하는 것 |
| Bean Validation | `@NotBlank`, `@Min` 같은 어노테이션으로 규칙을 선언하는 Java 표준(Jakarta Validation). Spring Boot의 `spring-boot-starter-validation`이 구현체를 넣어 줍니다 |
| DTO | 데이터를 옮기기 위한 객체. 여기서는 요청 본문 전용 `ProductRequest` |
| 예외(Exception) | 실행 중 정상 흐름을 멈추는 오류 신호. `throw`로 던지고 `catch`나 예외 처리기에서 받습니다 |
| `@RestControllerAdvice` | 모든 Controller의 예외를 한곳에서 받아 응답으로 바꾸는 Spring 기능 |
| record | 값만 담는 클래스를 짧게 선언하는 Java 문법 (Java 16+) |

## 2. 무엇을 하는 작업인가

**Story 3**: 상품 등록 `POST /products`에 `{"name": "   ", "price": 30000}`을 보내면, 서버는 Service나 DB에 가기 전에 400 `{"status": 400, "message": "name must not be blank"}`로 거절합니다. `{"name": "Mouse", "price": -1}`이면 `price must be greater than or equal to 0`입니다.

> 해설: 택배 접수 창구에서 주소가 비어 있거나 무게가 음수인 송장은 창구에서 바로 돌려보냅니다. 물류 창고(Service·DB)까지 보내지 않으니 창고에는 이상한 데이터가 쌓이지 않습니다.

**Story 4**: 어떤 오류든 응답 모양이 `{"status": 숫자, "message": "문장"}` 하나로 통일됩니다. 400 검증 오류, 404 없는 상품, 500 서버 내부 오류 모두 같은 모양이라 클라이언트는 `message`만 읽으면 됩니다. 500일 때는 `"Internal server error"`만 보여 주고, 실제 원인은 서버 로그에만 남깁니다.

> 해설: 매장마다 반품 영수증 양식이 다르면 본사가 모아 처리하기 어렵습니다. 양식을 하나로 정해 두면 누가 받아도 같은 칸에서 같은 정보를 찾을 수 있습니다. 단 "창고 시스템이 고장 났다" 같은 내부 사정은 영수증에 쓰지 않고 내부 일지(로그)에만 적습니다.

## 3. 요청이 지나가는 길

```
POST /products  {"name":"   ","price":30000}
  │
  ├─ ① JSON → ProductRequest 변환 (Jackson)
  │     실패: {"name":  처럼 깨진 JSON
  │       → handleHttpMessageNotReadable → 400 "Malformed JSON request or missing required field"
  │
  ├─ ② @Valid 검사 (Bean Validation)
  │     실패: name 공백
  │       → handleMethodArgumentNotValid → 400 "name must not be blank"
  │
  └─ ③ 통과 → ProductService.create → DB 저장 → 201
        Service에서 404 예외 → handleExceptionInternal → 404 "Product not found"
        예상 못 한 예외       → handleUnexpected      → 로그 + 500 "Internal server error"
```

## 4. 핵심 코드 읽기

### 4-1. 검증 규칙: ProductRequest

전체 역할: POST/PUT 요청 본문을 담는 DTO이며, 필드마다 규칙을 붙여 둡니다. Controller의 `@Valid`가 이 규칙을 실행합니다.

`src/main/java/com/example/product_api/ProductRequest.java:12–24` (원문. 16·18줄은 Story 7에서 추가)
```java
    // id는 DB가 정하므로 클라이언트가 보내면 400으로 거절한다
    @Null(message = "id must not be provided")
    private Long id;

    // DB 컬럼이 varchar(255)라서, 더 긴 이름은 DB에 가기 전에 400으로 거절한다
    @NotBlank(message = "name must not be blank")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    // int는 값이 빠지면 0이 되어 @Min(0)을 통과하므로, 누락을 잡기 위해 Integer + @NotNull 사용
    @NotNull(message = "price must not be null")
    @Min(value = 0, message = "price must be greater than or equal to 0")
    private Integer price;
```

- `@NotBlank(message = "...")`
  - 역할: `null`, 빈 문자열 `""`, 공백만 `"   "`을 모두 거절합니다.
  - 바뀌면: `@NotNull`로 바꾸면 `""`와 `"   "`가 통과해 이름 없는 상품이 저장됩니다. `@NotEmpty`는 `""`만 막고 `"   "`는 통과시킵니다.
  - 문법: Jakarta Validation 어노테이션. `message = "..."`는 어노테이션의 속성 값이며, 실패 시 이 문장이 응답 `message`에 들어갑니다.
- `@NotNull` + `@Min(value = 0)` + `Integer price`
  - 역할: 가격 누락은 `price must not be null`, 음수는 `price must be greater than or equal to 0`. 0원은 허용합니다.
  - 바뀌면: 타입을 `int`로 바꾸면 값이 빠졌을 때 Jackson이 0을 넣어 `@Min(0)`을 통과합니다. 가격을 깜빡한 요청이 0원 상품으로 저장됩니다.
  - 문법: `int`는 Java 기본형으로 `null`이 될 수 없고, `Integer`는 JDK 래퍼 클래스로 `null`이 될 수 있습니다. 이 차이가 "누락"을 알아챌 수 있는지를 가릅니다.
- `@Null`(id): 값이 있으면 실패합니다. 클라이언트가 id를 정하지 못하게 합니다.

### 4-2. 검증 실행: Controller의 `@Valid`

`src/main/java/com/example/product_api/ProductController.java:43` (원문 한 줄)
```java
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
```

- 역할: `@RequestBody`가 JSON을 `ProductRequest`로 바꾸고, `@Valid`가 위 규칙을 검사합니다. 실패하면 메서드 본문은 실행되지 않습니다.
- 빠지면: `@Valid`가 없으면 규칙이 붙어 있어도 **검사 자체를 하지 않습니다.** 공백 이름이 그대로 Service로 갑니다.
- 문법: `@Valid`는 Jakarta Validation, `@RequestBody`는 Spring 어노테이션입니다. 매개변수 앞에 붙는 어노테이션입니다. PUT(`ProductController.java:50`)에도 똑같이 붙어 있습니다.

### 4-3. 오류 응답 형식: ApiErrorResponse

`src/main/java/com/example/product_api/ApiErrorResponse.java:3–4` (원문)
```java
// 모든 API 오류 응답의 공통 JSON 형식: {"status":400,"message":"..."}
public record ApiErrorResponse(int status, String message) {
```

- 역할: 오류 응답 본문의 모양을 하나로 고정합니다. Jackson이 `{"status": 400, "message": "..."}`로 바꿉니다.
- 문법: Java **record**. `status`, `message` 필드, 생성자, `status()`·`message()` 접근 메서드, `equals`/`toString`을 컴파일러가 자동으로 만듭니다. 값은 만든 뒤 바꿀 수 없습니다.

### 4-4. 전역 예외 처리: GlobalExceptionHandler

전체 역할: Controller·Service에서 던진 예외를 받아 `ApiErrorResponse`로 바꿉니다. 어느 Controller에서 나든 Spring이 자동으로 이 클래스를 거치게 합니다.

`src/main/java/com/example/product_api/GlobalExceptionHandler.java:19–22` (원문)
```java
// ResponseEntityExceptionHandler를 상속하면 Spring MVC의 표준 예외(405, 415 등)도
// 원래 상태 코드를 유지한 채 이 클래스를 거치므로, 응답 형식을 한 곳에서 통일할 수 있다
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
```

- `@RestControllerAdvice`: 모든 `@RestController`에 적용되는 예외 처리 클래스라는 표시(Spring).
- `extends ResponseEntityExceptionHandler`: Spring이 미리 만든 처리기를 **상속**합니다. 405(허용 안 된 메서드), 415(지원 안 하는 Content-Type) 같은 표준 예외 처리가 이미 들어 있고, 우리는 필요한 메서드만 `@Override`로 바꿉니다.
  - 빠지면: 우리가 따로 적지 않은 Spring 예외는 Spring 기본 오류 형식으로 나가 형식이 둘로 갈립니다.

`src/main/java/com/example/product_api/GlobalExceptionHandler.java:34–38` (원문, `handleMethodArgumentNotValid` 본문)
```java
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message);
```

- 역할: 실패한 필드들의 메시지를 모아 알파벳순으로 정렬하고 `", "`로 이어 붙입니다.
  - 예: `{"id":5,"name":"","price":-1}` → `id must not be provided, name must not be blank, price must be greater than or equal to 0`
- `.sorted()`가 빠지면: 검사 순서가 실행마다 달라질 수 있어 같은 요청에 다른 문장이 나올 수 있습니다. 테스트도 불안정해집니다.
- 문법:
  - `getBindingResult().getFieldErrors()`: Spring의 검증 결과 API. 필드 오류 목록(`List<FieldError>`)을 돌려줍니다.
  - `.stream()` … `.collect(...)`: JDK **스트림 API**. 목록을 하나씩 흘려보내며 변환(`map`) → 정렬(`sorted`) → 합치기(`collect`)를 합니다.
  - `error -> error.getDefaultMessage()`: Java **람다**. "오류 하나를 받아 그 메시지를 돌려준다"는 짧은 함수입니다.
  - `Collectors.joining(", ")`: JDK 표준 API. 문자열들을 구분자로 이어 붙입니다.

`src/main/java/com/example/product_api/GlobalExceptionHandler.java:74–79` (원문)
```java
    // 예상하지 못한 오류: 원인은 서버 로그에만 남기고 응답에는 상세 정보를 숨긴다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("Unexpected server error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
```

- 역할: 위에서 처리하지 못한 모든 예외를 마지막에 받아 500으로 바꿉니다.
- 빠지면: 예외 종류에 따라 Spring 기본 오류 응답이 나가고, 설정에 따라 예외 메시지 등 내부 정보가 보일 수 있습니다.
- 문법:
  - `@ExceptionHandler(Exception.class)`: Spring 어노테이션. `Exception.class`는 Java의 **클래스 리터럴**(타입 자체를 값으로 넘김)입니다. `Exception`은 거의 모든 예외의 부모 클래스이므로, 더 구체적인 처리기가 없는 예외는 여기로 옵니다.
  - `log.error("...", ex)`: SLF4J 로깅 라이브러리. 두 번째 인자로 예외를 넘기면 원인(stack trace)이 로그에 남습니다.
  - `ResponseEntity<Object>`: Spring 클래스 + 제네릭. 본문 타입을 `Object`로 열어 둡니다.

참고로 404 같은 `ResponseStatusException`은 `handleExceptionInternal`(52–72줄)에서 reason을 꺼내 처리합니다. 이 부분은 Story 1~2 보고서에서 다뤘습니다.

## 5. 테스트 코드 읽기

`src/test/java/com/example/product_api/GlobalExceptionHandlerTest.java:96–104` (원문)
```java
    @Test
    void unexpectedErrorHidesInternalDetails() throws Exception {
        mockMvc.perform(get("/test/fail"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Internal server error"))
            .andExpect(content().string(not(containsString("secret internal detail"))))
            .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
```

- 테스트 전용 `FailingController`(같은 파일 29–35줄)가 일부러 `IllegalStateException("secret internal detail")`을 던집니다.
- `jsonPath("$.message")`(Spring Test): JSON 응답에서 `message` 값을 꺼내 비교합니다.
- `not(containsString(...))`(Hamcrest 라이브러리): 응답 본문에 비밀 문구와 예외 클래스명이 **없어야** 통과합니다. "보여 주면 안 되는 것을 보여 주지 않았다"를 확인하는 테스트입니다.
- 이 테스트는 500 오류가 나야 통과합니다. 의도한 오류를 올바른 형식으로 숨겨서 돌려준 것이 정상 동작입니다.

`src/test/java/com/example/product_api/ProductControllerTest.java:91–109` (원문 일부: 91~109줄)
```java
    void createProductReturnsBadRequestForInvalidInput() throws Exception {
        String[] invalidBodies = {
            "{\"price\":50000}",                         // name 누락
            "{\"name\":\"\",\"price\":50000}",           // name 빈 문자열
            "{\"name\":\"   \",\"price\":50000}",        // name 공백만
            "{\"name\":\"Keyboard\",\"price\":-1}",      // price < 0
            "{\"name\":\"Keyboard\"}"                    // price 누락
        };

        for (String body : invalidBodies) {
            mockMvc.perform(post("/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }

        // 잘못된 요청은 Service까지 가지 않으므로 아무것도 저장되지 않는다
        verifyNoInteractions(productService);
    }
```

- `String[]`와 향상된 `for` 문(Java 문법): 잘못된 본문 5개를 배열에 담아 하나씩 보냅니다.
- `\"`: Java 문자열 안의 큰따옴표 이스케이프입니다. 실제로 보내는 값은 `{"price":50000}`입니다.
- `verifyNoInteractions(productService)`(Mockito): 5번 모두 Service가 **한 번도 호출되지 않았는지** 확인합니다. 검증이 Service 앞에서 막는다는 증거입니다.
- 한계: 상태 코드만 확인합니다. 어떤 메시지가 나왔는지는 보지 않습니다(code-review.md 발견 사항 F1).

## 6. 성공과 실패 사례

아래는 **2026-11-12 로컬 실행**(Windows PowerShell, `.\mvnw.cmd -q test`, 실행 108·실패 0)에서 통과한 테스트의 실제 입력과 기대값입니다. MockMvc 테스트이며 실제 서버 결과가 아닙니다.

| # | 입력 | 처리 | 실제 결과 (테스트) | 기대값 | 테스트 |
|---|---|---|---|---|---|
| 1 | `POST` `{"name":"Free Sticker","price":0}` | 검증 통과(0원 허용) | 201 | 201 | `createProductAllowsZeroPrice` |
| 2 | `POST` `{"name":"   ","price":50000}` | `@NotBlank` 실패 | 400, `name must not be blank` | 같음 | `validationErrorReturnsFieldMessage` |
| 3 | `POST` `{"id":5,"name":"","price":-1}` | 3개 규칙 실패 → 정렬 후 연결 | 400, `id must not be provided, name must not be blank, price must be greater than or equal to 0` | 같음 | `multipleValidationErrorsAreJoined` |
| 4 | `POST` `{"name":` (깨진 JSON) | JSON 변환 실패 | 400, `Malformed JSON request or missing required field`, 본문에 `jackson` 없음 | 같음 | `malformedJsonReturnsBadRequestWithoutInternalDetails` |
| 5 | `GET /products/abc` | `Long` 변환 실패 | 400, `status` 400 | 400 | `invalidPathIdReturnsBadRequest` |
| 6 | `GET /test/fail` (테스트 전용) | `IllegalStateException` | 500, `Internal server error`, 내부 문구·클래스명 없음 | 같음 | `unexpectedErrorHidesInternalDetails` |

설명용 예측(직접 실행하지 않음): 서버에서 `api.http`의 `[400-4]`(`{"name": "Mouse"}`)를 보내면 400 `price must not be null`이 나올 것으로 예상합니다. 이 문구는 자동 테스트로 확인되지 않았습니다. 5번 사례의 `message`는 코드상 `Bad Request`일 것으로 보이지만 테스트는 상태 코드만 확인합니다.

## 7. 검증 한계와 해야 할 일

- Story 3~4 당시 코드·테스트·실행 결과는 남아 있지 않습니다. 위 결과는 현재 코드로 다시 실행한 것입니다.
- 현재 전체 테스트: 실행 108, 실패 0, 오류 0, 건너뜀 0 (2026-11-12, 로컬). Story 3~4만의 수가 아닙니다.
- 직접 확인: `.\mvnw.cmd spring-boot:run`으로 서버를 띄운 뒤 `api.http`의 `[400-1]`~`[400-10]`을 실행하고, 요청 제목에 적힌 메시지와 응답을 비교합니다. 400 요청은 DB를 바꾸지 않습니다.

## 8. 이해도 확인 질문

**Q1.** `ProductRequest.price`의 타입을 `Integer`에서 `int`로 바꾸면, `{"name": "Mouse"}` 요청은 어떻게 될까요?
> 해설: `int`는 `null`이 될 수 없어 값이 빠지면 0이 들어갑니다. `@NotNull`은 의미가 없어지고 `@Min(0)`도 통과하므로, 400 대신 0원짜리 Mouse 상품이 저장됩니다(201).

**Q2.** `createProduct`의 매개변수에서 `@Valid`만 지우고 `ProductRequest`의 어노테이션은 그대로 두면 `{"name": "   ", "price": 30000}`은 어떻게 될까요?
> 해설: 규칙은 선언만 되어 있고 실행되지 않습니다. 공백 이름이 Service로 넘어가 저장될 수 있습니다. `createProductReturnsBadRequestForInvalidInput`의 `verifyNoInteractions`가 실패합니다.

**Q3.** `handleMethodArgumentNotValid`에서 `.sorted()`를 지우면 어떤 문제가 생길 수 있을까요?
> 해설: 여러 필드가 동시에 틀렸을 때 메시지 순서가 검사 순서를 따르게 됩니다. 이 순서는 보장되지 않아, 같은 요청에도 다른 문장이 나올 수 있습니다. `multipleValidationErrorsAreJoined`처럼 문장 전체를 비교하는 테스트가 가끔 실패하는 불안정한 테스트가 됩니다.
