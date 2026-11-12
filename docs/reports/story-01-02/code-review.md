# Story 1~2 코드 리뷰 보고서 (소급 작성)

- 대상: Story 1 상품 삭제 API (204 / 404), Story 2 중복 상품 ID 등록 방지 (409, 이후 제거)
- 보고서 작성일: 2026-11-12
- Story 진행 시점: Git 도입 전. 첫 커밋(`72fcc5d`, 2026-10-26) 이전이며 정확한 날짜는 기록이 없습니다.
- 작성 방식: 작성자(AI)의 **자체 검토**입니다. 독립 리뷰가 아닙니다.

## 0. 먼저 알아 둘 한계

- Story 1~6은 Git을 쓰기 전에 진행했고, 그 결과가 첫 커밋 `72fcc5d` 하나에 섞여 있습니다. 그래서 **Story 1~2 당시의 코드와 당시 실행 결과는 남아 있지 않습니다.**
- README 기록에 따르면 Story 1~2 당시에는 상품을 메모리(`ArrayList`)에 저장했고, Story 6에서 MySQL + Spring Data JPA로 바꿨습니다. 이 보고서가 인용하는 코드는 **Story 6까지 끝난 첫 커밋의 코드**입니다. Story 1 당시 코드가 아닙니다.
- 삭제 관련 코드는 첫 커밋과 현재(`a14f647`)의 내용이 같습니다(파일 안의 줄 위치만 바뀜). 그래서 줄 번호는 현재 파일 기준으로 씁니다.
- Story 2의 409 처리는 Story 6에서 제거되어 **Git 어디에도 코드가 없습니다.** README의 한 줄 기록이 유일한 근거입니다.

## 1. 용어

| 용어 | 뜻 |
|---|---|
| 204 No Content | 요청은 성공했고 돌려줄 본문이 없다는 HTTP 상태 코드. 삭제 성공에 자주 씁니다 |
| 404 Not Found | 요청한 대상(여기서는 상품)이 없다는 상태 코드 |
| 409 Conflict | 요청이 현재 데이터 상태와 충돌한다는 상태 코드. Story 2에서 "이미 있는 id"에 썼습니다 |
| 경로 변수 | URL 안의 값. `/products/3`의 `3` |
| 트랜잭션 | 여러 DB 작업을 "전부 성공 또는 전부 취소"로 묶는 단위 |
| Mock(가짜 객체) | 테스트에서 진짜 DB 대신 넣는, 동작을 미리 정해 둔 객체 (Mockito 라이브러리) |

## 2. 작업 목표와 구현 범위

**Story 1** — 상품 하나를 id로 삭제합니다.
- 예: `Keyboard, 50,000원` 상품(id 1)이 있을 때 `DELETE /products/1` → 204, 본문 없음. 다시 `GET /products/1` → 404.
- 없는 상품 `DELETE /products/99` → 404 `{"status": 404, "message": "Product not found"}`.

**Story 2** (과거 기록) — 클라이언트가 이미 있는 id로 상품을 등록하면 409로 거절했습니다.
- Story 6에서 id를 DB가 자동으로 정하도록(`@GeneratedValue(IDENTITY)`) 바꾸면서 제거되었습니다.
- 대신 요청 본문에 id를 넣으면 400 `id must not be provided`로 거절합니다(`ProductRequest`의 `@Null`).

## 3. 수용 기준별 충족 여부

Story 1~2의 원래 수용 기준 문서는 남아 있지 않습니다. 아래는 README의 Story 표("상품 삭제 API (204 / 404)")와 현재 API 계약(CLAUDE.md)에서 정리한 기준입니다.

| 기준 | 충족 | 근거 |
|---|---|---|
| 있는 상품 삭제 시 204, 본문 없음 | 충족 | `ProductController.java:64–68`의 `@ResponseStatus(HttpStatus.NO_CONTENT)` + `void`. `ProductControllerTest.deleteProductReturnsNoContent` |
| 삭제 후 실제 DB에서 사라짐 | 충족 | `ProductApiIntegrationTest.java:60–62` — 삭제 후 `productRepository.count()`가 0 |
| 삭제 후 조회하면 404 | 충족 | `ProductApiIntegrationTest.java:64–66` — `Product not found` 확인 |
| 없는 상품 삭제 시 404, DB 삭제 호출 안 함 | 충족 | `ProductServiceTest.deleteThrowsNotFoundWhenMissing` — `verify(productRepository, never()).delete(any())` |
| 404 오류가 `{status, message}` 형식 | 충족 (간접) | 같은 `ResponseStatusException` 경로를 `GlobalExceptionHandlerTest.java:88–93`에서 확인. DELETE 경로에서 본문을 직접 확인하는 테스트는 없음 (발견 사항 F1) |
| (Story 2) 중복 id 등록 시 409 | 확인 불가 → 제거됨 | 코드 없음. 현재는 id를 보내면 400 (`ProductControllerTest.createProductRejectsBodyWithId`) |

## 4. 관련 파일과 역할 (현재 기준)

Story 1~2만의 변경 파일 목록은 Git에 없으므로, 삭제 기능에 관련된 파일만 정리합니다.

| 파일 | 역할 |
|---|---|
| `src/main/java/com/example/product_api/ProductController.java` | `DELETE /products/{id}` 받기, 204 지정 |
| `src/main/java/com/example/product_api/ProductService.java` | 상품 조회(없으면 404) 후 삭제, `@Transactional` |
| `src/main/java/com/example/product_api/ProductRepository.java` | `JpaRepository`가 `findById`, `delete` 제공 |
| `src/main/java/com/example/product_api/GlobalExceptionHandler.java` | 404 예외를 `{"status":404,"message":"Product not found"}`로 변환 |
| `src/main/java/com/example/product_api/ProductRequest.java` | (Story 2 대체) id를 보내면 400 |
| `src/test/java/.../ProductServiceTest.java`, `ProductControllerTest.java`, `ProductApiIntegrationTest.java` | 삭제 성공·실패 테스트 |
| `api.http` | `[삭제 1~3]`, `[404-3]` 수동 확인 요청 |

## 5. 실행과 데이터 흐름

```
api.http [삭제 2]  DELETE /products/1
  → ProductController.deleteProduct(1)
  → ProductService.delete(1)          (@Transactional 시작)
      → findById(1) → ProductRepository.findById(1) → SELECT
          있음 → ProductRepository.delete(product) → 커밋 시 DELETE → 204 (본문 없음)
          없음 → ResponseStatusException(404, "Product not found")
                 → GlobalExceptionHandler.handleExceptionInternal
                 → 404 {"status": 404, "message": "Product not found"}
```

- 실제 운영 DB는 MySQL, 테스트 DB는 H2입니다.
- 삭제는 먼저 SELECT로 있는지 확인한 뒤 DELETE합니다. 있는지를 먼저 봐야 404를 정확히 돌려줄 수 있기 때문입니다.

## 6. 설계 결정과 대안

| 결정 | 이유 | 대안 |
|---|---|---|
| 삭제 성공 시 204 + 본문 없음 | 삭제된 대상은 더 돌려줄 정보가 없음 | 200 + 삭제된 상품 JSON. 클라이언트가 삭제한 내용을 보여 줄 때 유용하지만 이 API에서는 쓰지 않음 |
| 없는 상품 삭제는 404 | 클라이언트가 잘못된 id를 알 수 있음 | 204로 "이미 없음"을 성공 처리(멱등 삭제). 재시도에는 편하지만 오타 id를 알아채기 어려움 |
| 조회 로직 `findById`를 삭제에서 재사용 | 404 규칙이 한 곳에만 있음 | `existsById` + `deleteById`. 쿼리 구조는 비슷하고 404 메시지를 따로 써야 함 |
| (Story 6) 409 중복 검사를 없애고 DB가 id 생성 | id가 겹칠 수 없으므로 검사할 이유가 사라짐 | 클라이언트 지정 id 유지 + 409. 클라이언트가 id 관리 책임을 짐 |

관련 ADR은 없습니다(`docs/adr`에는 Story 10 관련 `0001` 하나만 있음). 위 결정 이유는 README와 코드 주석(`ProductService.java` "id는 DB가 자동으로 정하므로 중복 검사가 필요 없다")에서 정리했고, 대안은 작성자가 덧붙인 비교입니다.

## 7. 자체 리뷰 발견 사항

검토 범위: `ProductController`·`ProductService`·`ProductRepository`의 삭제 경로, `GlobalExceptionHandler`의 404 처리, `ProductRequest`·`Product`의 id 규칙, 삭제 관련 테스트 5개, `api.http` 삭제 요청.

**확정한 결함 없음.** 아래는 개선 제안입니다.

| ID | 심각도 | 위치 | 문제 | 영향 | 제안 |
|---|---|---|---|---|---|
| F1 | 낮음 | `src/test/java/com/example/product_api/ProductControllerTest.java:200–207` | 없는 상품 DELETE 테스트가 상태 코드(404)만 확인하고 본문 `message`는 확인하지 않음 | DELETE 경로의 오류 형식이 바뀌어도 이 테스트는 통과함. 단 같은 예외 처리를 `GlobalExceptionHandlerTest:88–93`이 확인하므로 실제 위험은 작음 | 별도 `test` 작업에서 `.andExpect(jsonPath("$.message").value("Product not found"))` 추가 검토 |

## 8. 검증 결과

| 출처 | 내용 |
|---|---|
| 로컬 실행 (AI, 2026-11-12) | Windows PowerShell, `.\mvnw.cmd -q test`, 종료 코드 0. surefire 보고서 집계: **실행 108, 실패 0, 오류 0, 건너뜀 0** |
| 위 실행 중 삭제 관련 | `ProductServiceTest.deleteRemovesProduct`, `deleteThrowsNotFoundWhenMissing`, `ProductControllerTest.deleteProductReturnsNoContent`, `deleteProductReturnsNotFoundWhenProductDoesNotExist`, `ProductApiIntegrationTest.crudFlowWorksWithDatabase` 모두 통과 |
| 과거 기록 | 첫 커밋 README: "자동화 테스트 실행 (현재 29개)". Story 1~2 시점의 테스트 개수와 결과는 기록 없음 |
| 미검증 | 실제 MySQL 서버에 `api.http`로 삭제 요청. 이번에 실행하지 않음 (실제 DB 데이터를 바꾸는 작업이라 사용자가 직접 확인) |

108개는 Story 10과 이후 수정까지 포함한 현재 전체 테스트 수입니다. Story 1~2만의 테스트 수가 아닙니다. 이번 작업은 보고서만 추가했으므로 테스트 수는 작업 전과 같습니다.

MockMvc 테스트는 실제 서버를 띄우지 않고 Spring MVC를 흉내 내 요청을 보내는 방식입니다. 실제 서버·브라우저 확인과는 다릅니다.

## 9. 놓치기 쉬운 부분과 후속 조치

- **README의 Story 2 한 줄이 유일한 기록**입니다. 당시 409를 어떤 조건(POST만? PUT도?)으로 냈는지는 알 수 없습니다.
- **동시 삭제(추측, 미검증)**: 두 요청이 거의 동시에 같은 상품을 삭제하면, 둘 다 `findById`를 통과한 뒤 두 번째 요청이 404 대신 204를 받을 가능성이 있습니다. Spring Data JPA `delete`의 내부 동작에 따라 달라지며 이번에 확인하지 않았습니다. 포트폴리오 규모에서는 조치가 필요하지 않다고 봅니다.
- 사용자 확인 방법: 서버 실행 후 `api.http`의 `[삭제 1] → [삭제 2] → [삭제 3]` 순서로 실행(새로 만든 `Temp Item`만 삭제), `[404-3]`으로 없는 상품 삭제 확인.
- 후속: F1 테스트 보강은 필요하면 별도 `test/` 브랜치에서 진행.
