# Story 5~6 코드 리뷰 보고서 (소급 작성)

- 대상: Story 5 Controller / Service 계층 분리와 생성자 주입, Story 6 메모리(`ArrayList`) 저장에서 MySQL + Spring Data JPA로 전환, 요청 DTO 도입, 테스트 환경 분리
- 보고서 작성일: 2026-11-13
- Story 진행 시점: Git 도입 전. Story 6까지의 결과가 첫 커밋(`72fcc5d`, 2026-10-26)에 들어 있으며, Story별 정확한 날짜는 기록이 없습니다.
- 작성 방식: 작성자(AI)의 **자체 검토**입니다. 독립 리뷰가 아닙니다.

## 0. 먼저 알아 둘 한계

- Story 1~6은 첫 커밋 하나에 합쳐져 있습니다. **Story 5 직후의 코드(계층은 나뉘었지만 아직 `ArrayList`에 저장하던 코드)는 Git에 없습니다.** 첫 커밋은 Story 6까지 끝난 상태입니다.
- 그래서 Story 5는 "첫 커밋에 남은 계층 구조와 생성자 주입"을 근거로 검토합니다. Story 5가 무엇을 어떻게 바꿨는지 앞뒤를 비교할 수는 없습니다.
- 첫 커밋 이후 이 범위에서 바뀐 것은 아래와 같습니다(`git log 72fcc5d..HEAD -- <파일>`로 확인).

| 파일 | 이후 변경 | 내용 |
|---|---|---|
| `ProductController.java` | Story 7(`f3ddc83`), Story 10(`ea2f5e8`) | POST 201, 응답을 `ProductResponse`로 변환, 설명 저장 API 추가 |
| `ProductService.java` | Story 10(`ea2f5e8`) | `updateDescription` 추가. 기존 메서드는 그대로 |
| `Product.java` | Story 10(`ea2f5e8`) | `description` 필드 추가. 기존 필드는 그대로 |
| `ProductRepository.java` | 없음 | 첫 커밋과 같음 |
| `src/test/resources/application.properties` | Story 10(`ea2f5e8`), fix(`3188515`) | `ddl-auto=create-drop` → `validate` + Flyway, `open-in-view=false` |
| `src/main/resources/application-local.properties` | Story 10(`ea2f5e8`) | `ddl-auto=update` → `validate` |

- 당시 확인 내용 중 남아 있는 것은 첫 커밋 README의 "MySQL에 직접 접속해 `SELECT`로 확인", "서버 재시작 후에도 데이터 유지 확인"이라는 기록뿐입니다. 이 기록은 "과거 기록(사용자 수동 확인)"으로만 인용합니다.

## 1. 용어

| 용어 | 뜻 |
|---|---|
| 계층(Layer) | 역할별로 나눈 코드 묶음. 여기서는 Controller(HTTP) → Service(규칙·트랜잭션) → Repository(DB) |
| 의존성 주입(DI) | 객체가 필요한 다른 객체를 직접 `new`로 만들지 않고 밖(Spring)에서 받는 방식 |
| 생성자 주입 | DI를 생성자 매개변수로 받는 방식. 필드를 `final`로 둘 수 있습니다 |
| Bean | Spring이 만들어 관리하는 객체. `@RestController`, `@Service`가 붙은 클래스와 Repository 구현체 |
| JPA / Hibernate | Java 객체와 DB 테이블을 연결하는 표준(JPA)과 그 구현체(Hibernate) |
| Entity | DB 테이블 한 줄과 연결되는 클래스. 여기서는 `Product` |
| Spring Data JPA | 인터페이스만 선언하면 `save`, `findById` 같은 구현을 만들어 주는 라이브러리 |
| 트랜잭션 | 여러 DB 작업을 "모두 성공 또는 모두 취소"로 묶는 단위 |
| 변경 감지(Dirty Checking) | 트랜잭션 안에서 조회한 Entity 값을 바꾸면, 끝날 때 JPA가 바뀐 부분을 `UPDATE`로 반영하는 기능 |
| DTO | 데이터를 옮기기 위한 객체. 여기서는 요청 본문 전용 `ProductRequest` |
| H2 | 메모리에서 도는 가벼운 DB. 테스트에서 MySQL 대신 사용 |
| 프로필(Profile) | 실행 환경별로 설정 파일을 골라 쓰는 Spring 기능. 여기서는 `local` |
| `ddl-auto` | Hibernate가 테이블을 자동으로 만들거나 바꿀지 정하는 설정 |

## 2. 작업 목표와 구현 범위

**Story 5 — 계층 분리, 생성자 주입**
- `ProductController`는 HTTP 요청과 응답만 다루고, 조회·404·저장 같은 규칙은 `ProductService`로 옮깁니다.
- 두 클래스 모두 필요한 객체를 생성자로 받습니다. 직접 `new ProductService()`를 하지 않습니다.
- 예: `GET /products/99` → Controller는 `productService.findById(99)`만 호출 → Service가 "없음"을 판단해 404 예외를 던집니다.

**Story 6 — MySQL + JPA 전환, 요청 DTO, 테스트 환경 분리**
- 저장소를 `ArrayList`에서 MySQL로 바꾸고, `ProductRepository extends JpaRepository<Product, Long>`로 DB에 접근합니다. 서버를 재시작해도 데이터가 남습니다.
- id를 클라이언트가 아니라 DB(`AUTO_INCREMENT`)가 정합니다. 그래서 Story 2의 409 중복 검사를 없앴습니다.
- 요청 본문은 Entity(`Product`)가 아니라 DTO(`ProductRequest`)로 받습니다.
- 테스트는 MySQL 대신 H2를 쓰고, 개발용 `local` 프로필은 `spring-boot:run`에서만 적용합니다.

## 3. 수용 기준별 충족 여부

원래 수용 기준 문서는 남아 있지 않습니다. 첫 커밋 README의 Story 표·설계 결정 표와 현재 CLAUDE.md 계약에서 정리했습니다.

| 기준 | 충족 | 근거 |
|---|---|---|
| Controller에 비즈니스 로직 없음 | 충족 | `ProductController.java`의 각 메서드는 Service 호출 한 줄 + 응답 변환뿐(29–68줄). 404 판단은 `ProductService.findById`(23–29줄) |
| 의존성을 생성자로 받음 | 충족 | `ProductController.java:22–26`, `ProductService.java:13–17`. 클래스 안에 `new ProductService`/`new ProductRepository` 없음 |
| 생성자 주입 덕분에 가짜 객체로 단위 테스트 가능 | 충족 | `ProductServiceTest.java:29–30`, `ProductControllerTest.java:40–43`이 Spring 없이 `new`로 가짜 객체를 넣음 |
| 데이터가 DB에 저장됨 | 충족 | `ProductApiIntegrationTest.crudFlowWorksWithDatabase` — Repository로 다시 조회해 확인(43, 56–58, 62줄) |
| id는 DB가 생성, 서로 다름 | 충족 | `Product.java:16–18`(`IDENTITY`), `ProductApiIntegrationTest.databaseGeneratesDifferentIds` |
| 수정은 변경 감지로 DB 반영 | 충족 | `ProductService.update`(38–44줄)에 `save` 호출 없음. 통합 테스트 56–58줄에서 DB 값이 바뀐 것 확인 |
| 없는 상품 수정·삭제는 404, 삭제 시도 안 함 | 충족 | `ProductServiceTest.updateThrowsNotFoundWhenMissing`, `deleteThrowsNotFoundWhenMissing`(`never()).delete`) |
| 요청 본문은 DTO로 받음 | 충족 | `ProductController.java:43, 50`이 `ProductRequest`를 받음 |
| DB 계정은 코드에 없음 | 충족 | `src/main/resources/application.properties:5–6`이 `${DB_USERNAME}`, `${DB_PASSWORD}` 참조 |
| 테스트는 MySQL 없이 H2로 실행 | 충족 | `src/test/resources/application.properties:5`. CI(`.github/workflows/ci.yml`)도 MySQL 없이 실행하도록 설정됨 |
| `local` 프로필은 테스트에 적용되지 않음 | 충족 | `pom.xml`의 `spring-boot-maven-plugin` `<profiles>` 설정(첫 커밋부터 동일) |

## 4. 관련 파일과 역할 (현재 기준)

| 파일 | 역할 |
|---|---|
| `src/main/java/com/example/product_api/ProductController.java` | HTTP 매핑, `@Valid`, 상태 코드, 응답 변환 |
| `src/main/java/com/example/product_api/ProductService.java` | 404 판단, `@Transactional`, 등록·수정·삭제 |
| `src/main/java/com/example/product_api/ProductRepository.java` | `JpaRepository<Product, Long>` 선언만 있음 |
| `src/main/java/com/example/product_api/Product.java` | `products` 테이블과 연결되는 Entity |
| `src/main/java/com/example/product_api/ProductRequest.java` | 요청 DTO + 검증 규칙 |
| `src/main/resources/application.properties` | MySQL 연결, 환경 변수로 계정 참조 |
| `src/main/resources/application-local.properties` | `local` 프로필 설정 |
| `src/test/resources/application.properties` | 테스트용 H2 설정 (main 설정을 대신함) |
| `pom.xml` | `local` 프로필을 `spring-boot:run`에만 적용, H2는 `test` 범위 |
| `src/test/java/.../ProductServiceTest.java` | 가짜 Repository로 Service 규칙 확인 |
| `src/test/java/.../ProductApiIntegrationTest.java` | 실제 Spring + H2로 Controller부터 DB까지 확인 |

## 5. 실행과 데이터 흐름

```
PUT /products/1  {"name":"New Keyboard","price":55000}
  → ProductController.updateProduct          (HTTP: @PathVariable, @Valid)
  → ProductService.update  ── @Transactional 시작
       findById(1) → ProductRepository.findById → SELECT ... WHERE id=1
                     없으면 ResponseStatusException(404) → 트랜잭션 롤백
       product.setName / setPrice            (Java 객체 값만 바꿈)
     ── 메서드 끝 → 커밋 → Hibernate가 바뀐 값을 비교해 UPDATE products SET ... 실행
  → Controller가 응답 JSON 생성 → 200
```

```
설정 파일 선택
  .\mvnw.cmd spring-boot:run → main/application.properties + application-local.properties (MySQL)
  .\mvnw.cmd test            → test/application.properties 만 (H2)
                               ※ 같은 이름이라 main 파일은 읽히지 않음. 합쳐지는 것이 아니라 "대신" 쓰임
```

## 6. 설계 결정과 대안

첫 커밋 README "설계 결정은 개발자가 직접" 표에 Story 6 관련 결정 2개가 남아 있습니다(과거 기록).

| 결정 | 이유 (기록 또는 코드 근거) | 대안 |
|---|---|---|
| Entity 전환은 관련 계층을 **일괄 전환**, 단 내부 5단계로 나눠 진행 (AI 추천은 임시 호환 코드) | 곧 지울 임시 코드를 만들지 않고, 변경이 커지는 위험은 단계 분할로 관리 (README 기록) | 임시 호환 코드를 두고 한 계층씩 전환. 단계마다 동작하지만 버릴 코드가 생김 |
| 테스트에 개발용 프로필이 적용되는 문제 → **`spring-boot:run`에서만 `local` 적용** (B: 테스트에서 별도 프로필 강제 대신) | 증상만 막지 않고 원인(전역 환경 변수 `SPRING_PROFILES_ACTIVE`)을 제거 (README 기록) | 테스트 클래스마다 `@ActiveProfiles("test")`. 빠뜨리면 다시 MySQL 설정이 섞임 |
| 생성자 주입 | 필드를 `final`로 둘 수 있고, Spring 없이 `new`로 테스트 가능 | `@Autowired` 필드 주입. 짧지만 테스트에서 가짜를 넣기 어렵고 `final` 불가 |
| id를 DB가 생성(`IDENTITY`), `setId` 없음 | id 충돌 자체가 불가능해져 409 검사가 필요 없음 (코드 주석 근거) | 클라이언트 지정 id + 409. 클라이언트가 번호 관리 책임을 짐 |
| 요청은 DTO(`ProductRequest`)로 | Entity를 그대로 받으면 클라이언트가 id 같은 내부 필드까지 채울 수 있음 | Entity를 요청 본문으로. 클래스는 하나로 끝나지만 API와 DB 구조가 묶임 |
| 수정은 `save` 없이 변경 감지 | 트랜잭션 안에서 조회한 Entity는 JPA가 추적함 (코드 주석 근거) | `productRepository.save(product)` 명시 호출. 동작은 같고 의도가 더 눈에 보임 |
| DB 계정은 환경 변수, 테스트는 H2 | 저장소가 Public이라 비밀번호를 파일에 둘 수 없음. 테스트가 MySQL 데이터를 건드리지 않음 | 테스트도 MySQL. 실제 환경과 같지만 `deleteAll()`이 실제 데이터를 지울 위험 |

또 README "변경 범위 통제" 사례에, id 타입을 `int`에서 `Long`으로 바꾸던 중 AI가 `Long == Long` 비교 위험을 발견해 멈추고 보고했다는 기록이 있습니다. 해당 코드는 Git에 없어 실제 줄은 확인할 수 없습니다. 현재 `src/main/java`에는 id를 `==`로 비교하는 코드가 없습니다(검색으로 확인).

관련 ADR은 없습니다. 대안 비교 중 README에 없는 항목은 작성자가 덧붙였습니다.

## 7. 자체 리뷰 발견 사항

검토 범위: `ProductController`, `ProductService`, `ProductRepository`, `Product`, `ProductRequest`, 세 설정 파일, `pom.xml` 프로필 설정, `ProductServiceTest`, `ProductApiIntegrationTest`, `ProductControllerTest`의 `setUp`.

**확정한 결함 없음.** 아래는 개선 제안과 참고 사항입니다.

| ID | 심각도 | 위치 | 문제 | 영향 | 제안 |
|---|---|---|---|---|---|
| F1 | 낮음 | `src/test/java/com/example/product_api/ProductApiIntegrationTest.java:37` | `deleteAll()`이 안전한 이유는 `src/test/resources/application.properties`가 H2를 가리키기 때문뿐. 테스트 코드 자체에는 "지금 H2인지" 확인이 없음 | 설정 파일이 삭제·변경되면 실제 MySQL 데이터가 지워질 수 있음 (CLAUDE.md에 경고는 있음) | 별도 `test` 작업에서 DataSource URL이 `jdbc:h2:`로 시작하는지 확인하는 테스트나 가드 추가 검토 |
| F2 | 참고 | `src/main/java/com/example/product_api/Product.java` 전체 | 낙관적 잠금(`@Version`)이 없음. 두 사람이 같은 상품을 거의 동시에 PUT하면 나중 요청이 앞 요청을 조용히 덮어씀 | 포트폴리오 규모에선 문제 가능성 낮음 | 지금은 유지. 동시 수정이 중요해지면 별도 Story로 `@Version` + 409 검토 (스키마 변경이라 Flyway V3 필요) |
| F3 | 참고 | `src/main/java/com/example/product_api/ProductService.java:19–29` | 조회 메서드에 `@Transactional(readOnly = true)`가 없음 | 현재는 Entity에 연관관계가 없어 동작 차이 없음 (코드 근거 추론, 실행 확인 안 함) | 지금은 유지. 연관관계(지연 로딩)가 생기면 다시 검토 |
| F4 | 참고 | `src/test/java/com/example/product_api/ProductServiceTest.java:72–81` | 가짜 Repository를 쓰므로 변경 감지(UPDATE)는 이 테스트로 확인되지 않음. Java 객체 값이 바뀌었는지만 봄 | 오해 소지만 있음. DB 반영은 통합 테스트 56–58줄이 확인 | 조치 불필요. 두 테스트의 역할 차이를 이해하는 것이 중요 |

## 8. 검증 결과

이 보고서는 문서 작업이라 테스트를 실행하지 않았습니다. 아래는 이 범위를 확인하는 테스트와 그 역할입니다.

| 테스트 | 확인하는 것 |
|---|---|
| `ProductServiceTest` (첫 커밋부터 있던 조회·등록·수정·삭제와 404) | 가짜 Repository로 Service 규칙 확인. 없는 상품이면 404이고 `delete`를 호출하지 않음 |
| `ProductApiIntegrationTest.crudFlowWorksWithDatabase` | 등록 → 조회 → 수정 → 삭제를 H2에서 실행하고 Repository로 DB를 다시 조회 |
| `ProductApiIntegrationTest.databaseGeneratesDifferentIds` | DB가 서로 다른 id를 만듦 |
| `ProductApiIntegrationTest.invalidRequestDoesNotSaveAnything` | 잘못된 요청(본문에 id 포함)이 DB에 저장되지 않음 |
| `ProductApiApplicationTests` | Spring 컨텍스트가 뜨는지 |

| 출처 | 내용 |
|---|---|
| 과거 기록 (첫 커밋 README) | 실제 서버 API 요청, MySQL `SELECT` 확인, 서버 재시작 후 데이터 유지 확인 — 사용자가 수동으로 한 것이며 당시 출력은 남아 있지 않음 |
| 미검증 | 실제 MySQL 연결과 재시작 후 데이터 유지 (실제 DB 접근은 사용자 작업) |
| 코드 근거 추론 | `ProductService.update`에서 `@Transactional`을 지우면 `crudFlowWorksWithDatabase` 56–58줄이 실패할 것 — 실제로 지워 실행하지는 않음 |

통합 테스트는 MockMvc로 요청을 흉내 낸 것이며, 실제 서버 포트를 연 확인이 아닙니다.

## 9. 놓치기 쉬운 부분과 후속 조치

- **테스트 설정 파일은 main 설정을 "대신"합니다.** 합쳐지지 않으므로, main에만 있는 `ai.*` 설정은 테스트에 없습니다. 그래서 테스트가 기본 Stub 모드가 됩니다. 반대로 테스트 파일이 없어지면 main의 MySQL 설정이 그대로 쓰입니다(F1).
- 당시에는 테스트가 `ddl-auto=create-drop`, 로컬이 `update`였습니다. Story 10부터 둘 다 `validate`이고 테이블은 Flyway가 만듭니다. 지금 코드에서 `ddl-auto`를 당시 값으로 되돌리면 안 됩니다.
- `update`는 `return product;`로 끝나지만 실제 `UPDATE` SQL은 **메서드가 끝난 뒤 커밋할 때** 실행됩니다. 그 사이 DB 오류가 나면 Controller에 예외가 전달됩니다.
- `delete`는 `findById`로 먼저 SELECT한 뒤 DELETE합니다. 쿼리가 2번이지만, 그 덕분에 없는 id는 404로 구분됩니다.
- 사용자 확인 방법: `.\mvnw.cmd spring-boot:run`으로 서버를 띄우고 `api.http`로 상품을 등록합니다. 서버를 껐다 켠 뒤 `GET /products`로 같은 상품이 남아 있는지 확인합니다. MySQL에서 `SELECT * FROM products;`로 직접 볼 수도 있습니다. 등록한 확인용 상품은 같은 파일의 DELETE 요청으로 지웁니다.
- 후속: F1 가드 테스트는 필요하면 별도 `test/` 브랜치에서 진행합니다.
