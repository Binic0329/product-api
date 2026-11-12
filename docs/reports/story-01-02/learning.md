# Story 1~2 학습 보고서 (소급 작성)

- 대상: Story 1 상품 삭제 API, Story 2 중복 상품 ID 등록 방지(409, 이후 제거)
- 보고서 작성일: 2026-11-12 / Story 진행 시점: Git 도입 전(첫 커밋 2026-10-26 이전, 정확한 날짜 기록 없음)
- 인용 코드: 첫 커밋(Story 6까지 반영된 상태)과 현재 코드가 같은 부분만 골랐습니다. **Story 1~2 당시의 메모리(`ArrayList`) 저장 코드는 Git에 없어서 인용할 수 없습니다.** 줄 번호는 현재 파일 기준입니다.

## 1. 용어 정리

| 용어 | 풀이 |
|---|---|
| HTTP 메서드 `DELETE` | "이 주소의 자원을 지워 달라"는 요청 종류 |
| 상태 코드 204 / 404 / 409 / 400 | 성공·본문 없음 / 대상 없음 / 현재 상태와 충돌 / 요청 형식이 잘못됨 |
| 어노테이션 | `@`로 시작하는 표시. 클래스나 메서드에 붙여 Spring 같은 라이브러리에 "이렇게 처리해 달라"고 알려 줍니다 |
| 트랜잭션 | DB 작업 묶음. 중간에 실패하면 전부 되돌립니다 |
| Entity | DB 테이블 한 줄과 연결되는 Java 객체 (`Product`) |
| Mock | 테스트용 가짜 객체. "이 메서드를 부르면 이 값을 돌려줘"라고 미리 정합니다 |

## 2. 무엇을 하는 작업인가

**Story 1**: `Keyboard, 50,000원` 상품(id 1)을 `DELETE /products/1`로 지우면 서버는 204를 돌려주고 본문은 비어 있습니다. 같은 요청을 다시 보내거나 `GET /products/1`을 보내면 404 `{"status": 404, "message": "Product not found"}`가 옵니다.

> 해설: 도서관 대출 목록에서 책 한 권을 빼는 것과 비슷합니다. 사서는 "뺐습니다"라고만 하고 따로 영수증(본문)을 주지 않습니다(204). 목록에 없는 책 번호를 대면 "그런 책은 없습니다"라고 답합니다(404).

**Story 2** (과거 기록): Story 1~5 시절에는 클라이언트가 `{"id": 1, "name": "Keyboard", "price": 50000}`처럼 id를 직접 정해 등록했습니다. 이미 id 1이 있는데 또 id 1로 등록하면 409로 거절했습니다. Story 6에서 id를 DB가 정하게 바꾸면서 이 검사가 필요 없어졌고, 지금은 id를 보내는 것 자체를 400으로 거절합니다.

> 해설: 예전에는 손님이 사물함 번호를 직접 고르게 해서 "그 번호는 이미 사용 중입니다"(409) 안내가 필요했습니다. 지금은 관리실이 빈 번호를 배정해 주므로 겹칠 일이 없고, 손님이 번호를 고르려 하면 "번호는 저희가 정합니다"(400)라고 안내합니다.

## 3. 요청이 지나가는 길

```
api.http  DELETE /products/1
   │
   ▼
ProductController.deleteProduct(1)      ← URL의 1을 Long id로 받음
   │
   ▼
ProductService.delete(1)                ← @Transactional
   ├─ findById(1)
   │    └─ ProductRepository.findById(1) → SELECT ... WHERE id = 1
   │         ├─ 있음 → ProductRepository.delete(product) → DELETE → 204, 본문 없음
   │         └─ 없음 → ResponseStatusException(404, "Product not found")
   │                     → GlobalExceptionHandler → {"status":404,"message":"Product not found"}
```

## 4. 핵심 코드 읽기

### 4-1. Controller: 삭제 요청을 받는 곳

전체 역할: HTTP 요청을 받아 Service에 넘기고 상태 코드만 정합니다. 입력은 URL의 id, 출력은 없음(204).

`src/main/java/com/example/product_api/ProductController.java:64–68` (원문)
```java
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.delete(id);
    }
```

- `@DeleteMapping("/{id}")`
  - 역할: `DELETE /products/{id}` 요청을 이 메서드로 연결합니다. 클래스에 붙은 `@RequestMapping("/products")`(19줄)와 합쳐져 전체 주소가 됩니다.
  - 빠지면: 이 메서드로 가는 길이 없어 Spring이 405(허용되지 않은 메서드)를 돌려줍니다.
  - 문법: Spring MVC 어노테이션. `{id}`는 URL 일부를 변수로 받겠다는 표시입니다.
- `@ResponseStatus(HttpStatus.NO_CONTENT)`
  - 역할: 정상 종료 시 상태 코드를 204로 정합니다.
  - 빠지면: `void` 메서드는 기본 200을 돌려줍니다. 계약(204)과 달라집니다.
  - 문법: Spring 어노테이션. `HttpStatus.NO_CONTENT`는 Spring이 만든 enum(정해진 값 목록) 상수입니다.
- `@PathVariable Long id`
  - 역할: URL의 `{id}` 자리 값(`"1"`)을 `Long` 1로 바꿔 넣습니다.
  - 바뀌면: `/products/abc`처럼 숫자가 아니면 변환에 실패해 400이 됩니다.
  - 문법: Spring 어노테이션 + Java 매개변수. `Long`은 JDK의 정수 래퍼 클래스입니다.
- `productService.delete(id);` — 실제 판단은 Service가 합니다. Controller에는 비즈니스 로직을 두지 않습니다. `productService`는 생성자에서 받은 `private final` 필드입니다(22–26줄, **생성자 주입**: Spring이 객체를 만들 때 필요한 Service를 넣어 줌).

### 4-2. Service: 있는지 보고 지우기

전체 역할: 상품이 있는지 확인하고, 있으면 삭제합니다. Controller가 호출합니다. 입력 id, 출력 없음, 없으면 예외.

`src/main/java/com/example/product_api/ProductService.java:23–29` (원문)
```java
    public Product findById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Product not found"
            ));
    }
```

`src/main/java/com/example/product_api/ProductService.java:54–58` (원문)
```java
    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        productRepository.delete(product);
    }
```

- `productRepository.findById(id)` → `Optional<Product>`
  - 역할: 상품이 있으면 담고, 없으면 빈 상자를 돌려줍니다.
  - 문법: `findById`는 Spring Data JPA가 만들어 주는 메서드, `Optional`은 JDK 표준 클래스입니다. "값이 없을 수도 있음"을 `null` 대신 타입으로 표현합니다.
- `.orElseThrow(() -> new ResponseStatusException(...))`
  - 역할: 상자가 비었으면 404 예외를 던집니다.
  - 빠지면(예: `.get()` 사용): 비었을 때 `NoSuchElementException`이 나서 500 `Internal server error`가 됩니다.
  - 문법: `() -> ...`는 Java **람다**입니다. "필요할 때 이 예외를 만들어라"라는 짧은 함수로, 상품이 있으면 예외 객체를 아예 만들지 않습니다. `ResponseStatusException`은 Spring이 제공하는 "상태 코드를 가진 예외"입니다.
- `Product product = findById(id);` (delete 안)
  - 역할: 404 규칙을 다시 쓰지 않고 재사용합니다. 그래서 GET과 DELETE의 404 메시지가 항상 같습니다.
  - 빠지면: 없는 id를 삭제해도 오류 없이 204가 나갈 수 있습니다.
- `@Transactional`
  - 역할: 조회와 삭제를 한 트랜잭션으로 묶습니다. 메서드가 정상 종료하면 커밋되어 실제 DELETE가 반영됩니다.
  - 문법: Spring 어노테이션. Spring이 메서드 앞뒤에 "트랜잭션 시작/커밋·롤백" 코드를 대신 끼워 넣습니다.

### 4-3. Repository: 코드 없이 생기는 DB 메서드

`src/main/java/com/example/product_api/ProductRepository.java:6–7` (원문)
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

- 역할: 몸통이 비어 있지만 `findById`, `delete` 등을 쓸 수 있습니다. Spring Data JPA가 실행 시점에 구현체를 만들어 줍니다.
- 문법: `interface ... extends`는 Java 인터페이스 상속입니다. `<Product, Long>`은 **제네릭**으로, "다루는 Entity는 `Product`, id 타입은 `Long`"이라는 뜻입니다. 그래서 `findById(Long)`이 `Optional<Product>`를 돌려줍니다.

### 4-4. 오류 응답으로 바꾸는 곳

`src/main/java/com/example/product_api/GlobalExceptionHandler.java:61–71` (일부 발췌, 원문 줄 그대로)
```java
        String message = null;
        if (ex instanceof ResponseStatusException responseStatusException) {
            message = responseStatusException.getReason();
        }
        if (message == null) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            message = status != null ? status.getReasonPhrase() : "Error";
        }
        return ResponseEntity.status(statusCode)
            .headers(headers)
            .body(new ApiErrorResponse(statusCode.value(), message));
```

- 역할: Service가 던진 404 예외의 reason(`"Product not found"`)을 꺼내 `{"status": 404, "message": "Product not found"}`로 만듭니다.
- 빠지면: Spring 기본 오류 형식(`timestamp`, `path` 등)이 나가서 이 프로젝트의 오류 형식이 깨집니다.
- 문법:
  - `ex instanceof ResponseStatusException responseStatusException` — Java 16부터 쓸 수 있는 **패턴 매칭**. 타입 확인과 변수 선언을 한 번에 합니다.
  - `조건 ? A : B` — Java 삼항 연산자.
  - `ApiErrorResponse`는 `public record ApiErrorResponse(int status, String message)`로 선언된 Java **record**입니다. 필드·생성자·getter를 자동으로 만들어 주는 "값만 담는 클래스"입니다.
  - `ResponseEntity`는 Spring의 "상태 코드 + 헤더 + 본문" 묶음입니다.

### 4-5. Story 2 대신 들어온 규칙 (Story 6 코드)

`src/main/java/com/example/product_api/ProductRequest.java:12–14` (원문)
```java
    // id는 DB가 정하므로 클라이언트가 보내면 400으로 거절한다
    @Null(message = "id must not be provided")
    private Long id;
```

`src/main/java/com/example/product_api/Product.java:15–18` (원문)
```java
    // id는 DB가 자동으로 1, 2, 3... 증가시켜 정한다 (MySQL AUTO_INCREMENT)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
```

- `@GeneratedValue(strategy = GenerationType.IDENTITY)`: DB(MySQL `AUTO_INCREMENT`)가 id를 정합니다. 서로 다른 상품이 같은 id를 가질 수 없으므로 Story 2의 409 검사가 필요 없어졌습니다. JPA(Jakarta Persistence) 어노테이션입니다.
- `@Null(...)`: 요청에 id가 들어 있으면 검증 실패 → 400 `id must not be provided`. Bean Validation(Jakarta Validation) 어노테이션이며, Controller의 `@Valid`가 있어야 동작합니다.

## 5. 테스트 코드 읽기

`src/test/java/com/example/product_api/ProductServiceTest.java:123–140` (원문)
```java
    @Test
    void deleteRemovesProduct() {
        Product keyboard = product(1L, "Keyboard", 50000);
        when(productRepository.findById(1L)).thenReturn(Optional.of(keyboard));

        productService.delete(1L);

        verify(productRepository).delete(keyboard);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
        verify(productRepository, never()).delete(any());
    }
```

- `when(...).thenReturn(...)` (Mockito): 가짜 Repository에 "id 1을 찾으면 Keyboard를 돌려줘"라고 미리 정합니다. 진짜 DB 없이 Service 규칙만 검사할 수 있습니다.
- `verify(productRepository).delete(keyboard)` (Mockito): Service가 정말 그 상품으로 `delete`를 불렀는지 확인합니다.
- `verify(productRepository, never()).delete(any())`: 없는 상품일 때 **삭제를 시도조차 하지 않았는지** 확인합니다. 404를 던지기만 하고 뒤에서 삭제를 불러 버리는 실수를 잡습니다.
- `assertThatThrownBy(() -> ...)` (AssertJ): 람다 안의 코드가 예외를 던지는지 확인합니다. 여기서는 **예외가 나야 통과**합니다. 의도한 오류를 올바르게 낸 것이 정상 동작입니다.
- `1L`, `99L`: 숫자 뒤 `L`은 `long` 리터럴이라는 Java 문법입니다(`Long` 매개변수에 맞춤).

## 6. 성공과 실패 사례

아래는 **2026-11-12 로컬 실행**(Windows PowerShell, `.\mvnw.cmd -q test`)에서 통과한 테스트의 실제 입력과 기대값입니다. 실제 MySQL 서버에 보낸 결과가 아닙니다.

| # | 입력 | 처리 | 실제 결과 (테스트) | 기대값 | 테스트 |
|---|---|---|---|---|---|
| 1 | `Keyboard, 50000` 등록 후 `DELETE /products/{id}` (H2) | Controller → Service → Repository → H2 | 204, `productRepository.count()` = 0 | 204, DB에서 사라짐 | `ProductApiIntegrationTest.crudFlowWorksWithDatabase` |
| 2 | 1번 직후 `GET /products/{id}` | `findById` → 빈 Optional → 404 예외 | 404, `message` = `Product not found` | 같음 | 위와 같음 |
| 3 | `DELETE /products/99` (가짜 Repository에 99 없음) | `findById` → 404 예외 | 404 예외, `delete` 호출 0번 | 같음 | `ProductServiceTest.deleteThrowsNotFoundWhenMissing` |
| 4 | `POST /products` 본문 `{"id":5,"name":"Keyboard","price":50000}` | `@Valid` → `@Null` 실패 | 400, `id must not be provided`, Service 호출 0번 | 같음 | `ProductControllerTest.createProductRejectsBodyWithId` |

설명용 예측(직접 실행하지 않음): 서버를 띄우고 `api.http`의 `[삭제 1]`로 `Temp Item, 1000원`을 만든 뒤 `[삭제 2]`를 보내면 204, `[삭제 3]`은 404 `{"status": 404, "message": "Product not found"}`가 나올 것으로 예상합니다.

## 7. 검증 한계와 해야 할 일

- Story 1~2 당시 코드·테스트·실행 결과는 남아 있지 않습니다(과거 기록 없음). 위 결과는 모두 현재 코드로 다시 실행한 것입니다.
- 현재 전체 테스트: 실행 108, 실패 0, 오류 0, 건너뜀 0 (2026-11-12, 로컬). Story 1~2만의 수가 아닙니다.
- 실제 MySQL에서 확인하려면: `.\mvnw.cmd spring-boot:run`으로 서버를 띄운 뒤 `api.http`의 `[삭제 1~3]`, `[404-3]`을 실행합니다. `[삭제 2]`는 `[삭제 1]`에서 새로 만든 상품만 지웁니다.

## 8. 이해도 확인 질문

**Q1.** `ProductController.deleteProduct`에서 `@ResponseStatus(HttpStatus.NO_CONTENT)`를 지우면 어떤 응답이 나갈까요?
> 해설: `void` 메서드이므로 본문 없이 기본 상태 코드 200이 나갑니다. 삭제는 그대로 되지만 API 계약(204)과 달라지고 `deleteProductReturnsNoContent` 테스트가 실패합니다.

**Q2.** `ProductService.delete`가 `findById(id)`를 부르지 않고 바로 `productRepository.deleteById(id)`만 부르게 바꾸면, 테스트 중 무엇이 깨질 수 있을까요?
> 해설: 없는 상품을 삭제할 때 우리 코드가 404 예외를 던지지 않게 됩니다. `deleteThrowsNotFoundWhenMissing`은 가짜 Repository를 쓰므로 예외가 나지 않아 실패합니다. 실제 DB에서는 Spring Data JPA 버전에 따라 조용히 넘어가거나 다른 예외가 날 수 있어, 404 계약을 보장할 수 없습니다.

**Q3.** 지금 `POST /products`에 `{"id": 1, "name": "Mouse", "price": 30000}`을 보내면 409와 400 중 무엇이 나오고, 왜 그럴까요?
> 해설: 400 `id must not be provided`입니다. Story 6에서 id를 DB가 정하게 바꾸면서 Story 2의 409 중복 검사를 없앴고, `ProductRequest`의 `@Null`이 id가 들어온 요청을 Service에 닿기 전에 거절합니다.
