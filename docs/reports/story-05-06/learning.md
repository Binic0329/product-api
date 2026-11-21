# Story 5~6 학습 보고서 (소급 작성)

- 대상: Story 5 Controller / Service 계층 분리와 생성자 주입, Story 6 MySQL + Spring Data JPA 전환, 요청 DTO, 테스트 환경 분리
- 보고서 작성일: 2026-11-13 / Story 진행 시점: Git 도입 전(첫 커밋 2026-10-26 이전, 정확한 날짜 기록 없음)
- 인용 코드: 현재 코드입니다. 인용한 줄은 첫 커밋과 같은 부분이며, 이후 바뀐 부분은 해당 위치에 표시했습니다. **Story 5 직후(계층은 나뉘었지만 아직 `ArrayList`에 저장하던) 코드는 Git에 없습니다.** 줄 번호는 현재 파일 기준입니다.

## 1. 용어 정리

| 용어 | 풀이 |
|---|---|
| 계층(Layer) | 역할별로 나눈 코드 묶음. Controller(HTTP 창구) → Service(업무 규칙) → Repository(DB 담당) |
| Bean | Spring이 직접 만들어 보관하는 객체. `@RestController`, `@Service`를 붙이면 Bean이 됩니다 |
| 의존성 주입(DI) | 필요한 객체를 스스로 `new`로 만들지 않고 Spring에게 받는 것 |
| JPA / Hibernate | Java 객체를 DB 테이블에 저장·조회하게 해 주는 표준(JPA)과 그 구현체(Hibernate) |
| Entity | DB 테이블 한 줄과 연결되는 클래스(`Product`) |
| 영속성 컨텍스트 | JPA가 트랜잭션 동안 조회한 Entity를 보관하고 변경을 추적하는 공간 |
| 트랜잭션 | 여러 DB 작업을 "모두 성공 또는 모두 취소"로 묶는 단위 |
| 변경 감지(Dirty Checking) | 트랜잭션 안에서 Entity 값을 바꾸면, 끝날 때 JPA가 알아서 `UPDATE`를 실행하는 것 |
| DTO | 계층 사이에 데이터를 옮기는 용도의 객체. 요청 본문 전용 `ProductRequest` |
| H2 | 메모리에서 도는 DB. 테스트가 끝나면 사라집니다 |
| Mock(가짜 객체) | 진짜처럼 메서드를 갖췄지만 정해 준 값만 돌려주는 테스트용 객체(Mockito) |

## 2. 무엇을 하는 작업인가

**Story 5**: 상품 API의 코드를 "창구"와 "업무 담당"으로 나눕니다. `GET /products/99`가 들어오면 `ProductController`는 "99번 상품을 찾아 주세요"라고 `ProductService`에 부탁만 합니다. 99번이 없다는 판단과 404 예외는 Service가 합니다. Controller는 Service를 직접 만들지 않고, 생성자로 받아서 씁니다.

> 해설: 은행 창구 직원이 고객 응대와 서류 확인만 하고, 실제 대출 심사는 심사팀이 하는 구조입니다. 심사 기준이 바뀌어도 창구 업무는 그대로입니다. 창구 직원이 심사팀을 "직접 채용"하지 않고 회사(Spring)가 배정해 주기 때문에, 연습할 때는 가짜 심사팀(Mock)을 붙여 창구 업무만 따로 연습할 수 있습니다.

**Story 6**: `{"name": "Keyboard", "price": 50000}`을 등록하면 이제 메모리의 `ArrayList`가 아니라 MySQL `products` 테이블에 한 줄이 생깁니다. id는 DB가 1, 2, 3… 순서로 정합니다. 서버를 껐다 켜도 Keyboard가 남아 있습니다. 테스트는 MySQL 대신 H2 메모리 DB에서 돌아서, 테스트가 실제 데이터를 지우지 않습니다.

> 해설: `ArrayList`는 화이트보드입니다. 전원을 끄면(서버 재시작) 다 지워집니다. MySQL은 장부입니다. 그리고 테스트는 장부 대신 연습장(H2)에 씁니다. 연습장은 아무리 지워도 장부에 영향이 없습니다.

## 3. 요청이 지나가는 길

```
PUT /products/1   {"name":"New Keyboard","price":55000}
  │
  ├─ ProductController.updateProduct   ← HTTP만: URL의 id, 본문 검증(@Valid)
  │
  ├─ ProductService.update             ← @Transactional 시작
  │     ├─ findById(1)
  │     │    └─ ProductRepository.findById → SELECT ... FROM products WHERE id = 1
  │     │         없음 → ResponseStatusException(404) → 롤백 → {"status":404,"message":"Product not found"}
  │     ├─ product.setName("New Keyboard"), setPrice(55000)   ← Java 객체만 바뀜, SQL 아직 없음
  │     └─ return product
  │   메서드 종료 → 커밋 → Hibernate가 처음 값과 비교 → UPDATE products SET name=?, price=? WHERE id=1
  │
  └─ Controller가 JSON으로 변환 → 200 {"id":1,"name":"New Keyboard","price":55000,"description":null}
```

설정 파일은 실행 방법에 따라 이렇게 갈립니다.

```
.\mvnw.cmd spring-boot:run  → main/application.properties + application-local.properties → MySQL
.\mvnw.cmd test             → test/application.properties (main 파일을 대신함)          → H2
```

## 4. 핵심 코드 읽기

### 4-1. 생성자 주입: ProductController와 ProductService

전체 역할: Controller는 Service를, Service는 Repository를 생성자로 받습니다. Spring이 서버 시작 때 Bean을 만들면서 이 생성자를 호출해 알맞은 객체를 넣어 줍니다. 입력은 Spring이 넣어 주는 Bean, 출력은 없음(필드에 보관).

`src/main/java/com/example/product_api/ProductController.java:18–26` (원문)
```java
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }
```

- `@RestController` — 역할: 이 클래스를 HTTP 요청을 받는 Bean으로 등록하고, 반환값을 JSON으로 바꿉니다. 빠지면: Spring이 이 클래스를 모르니 `/products`가 404가 됩니다. 문법: Spring 어노테이션(라이브러리 API).
- `private final ProductService productService;` — 역할: Service를 보관합니다. `final`이면: 생성자에서 한 번 넣은 뒤 바꿀 수 없어, 실수로 `null`이나 다른 객체로 바뀌지 않습니다. 문법: Java 언어의 `final` 필드는 생성자에서 반드시 값을 넣어야 컴파일됩니다.
- 생성자 — 역할: Spring이 `ProductService` Bean을 찾아 이 생성자에 넣습니다. 생성자가 하나뿐이면 `@Autowired`를 쓰지 않아도 됩니다(Spring 규칙). 이게 없고 `new ProductService(...)`를 직접 쓰면: Service가 필요로 하는 Repository까지 Controller가 직접 만들어야 하고, 테스트에서 가짜 Service로 바꿀 수 없습니다.

`src/main/java/com/example/product_api/ProductService.java:10–17` (원문) — 같은 방식으로 Repository를 받습니다.
```java
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }
```

> 해설: 생성자 주입 덕분에 테스트가 이렇게 짧아집니다. `ProductServiceTest.java:29–30`의 `productService = new ProductService(productRepository);`는 Spring 없이 가짜 Repository를 꽂은 것입니다. 필드 주입(`@Autowired private ProductRepository productRepository;`)이었다면 생성자로 넣을 방법이 없어 Spring을 띄우거나 리플렉션을 써야 합니다.

### 4-2. Repository: 인터페이스만 선언

전체 역할: DB 접근 담당입니다. 구현 코드가 한 줄도 없지만, Spring Data JPA가 서버 시작 때 구현체를 만들어 Bean으로 등록합니다. Service가 `findAll`, `findById`, `save`, `delete`를 호출합니다.

`src/main/java/com/example/product_api/ProductRepository.java:5–7` (원문)
```java
// 인터페이스만 선언하면 Spring Data JPA가 findAll, findById, save, delete 등을 자동으로 구현해 준다
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

- `extends JpaRepository<Product, Long>` — 역할: "Entity는 `Product`, id 타입은 `Long`"이라고 알려 줍니다. 바뀌면: `<Product, Integer>`처럼 id 타입이 Entity와 다르면 `findById(1L)`의 타입이 맞지 않아 컴파일 오류나 시작 오류가 납니다. 문법: `<...>`는 Java 언어의 제네릭(타입 매개변수)이고, `JpaRepository`는 Spring Data JPA(라이브러리 API)입니다. 인터페이스가 인터페이스를 상속할 때도 `extends`를 씁니다.

### 4-3. Entity: Product

전체 역할: `products` 테이블 한 줄과 연결됩니다. JPA가 DB에서 읽은 값으로 이 객체를 만들고, 이 객체의 값을 DB에 씁니다.

`src/main/java/com/example/product_api/Product.java:11–38` (원문. 26–28줄 `description`은 Story 10에서 추가되어 여기서는 생략)
```java
@Entity
@Table(name = "products")
public class Product {

    // id는 DB가 자동으로 1, 2, 3... 증가시켜 정한다 (MySQL AUTO_INCREMENT)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    // JPA가 DB에서 읽어 온 값으로 객체를 만들 때 사용하는 기본 생성자
    protected Product() {

    }

    public Product(String name, Integer price) {
        this.name = name;
        this.price = price;
    }
```

- `@Entity`, `@Table(name = "products")` — 역할: 이 클래스를 `products` 테이블과 연결합니다. `@Entity`가 빠지면: `JpaRepository<Product, Long>`이 관리 대상이 아니라서 서버 시작이 실패합니다. 문법: Jakarta Persistence(JPA) 어노테이션.
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` — 역할: id를 DB의 `AUTO_INCREMENT`에 맡깁니다. 그래서 `new Product("Keyboard", 50000)` 직후의 id는 `null`이고, `save` 후에야 1 같은 값이 채워집니다. 빠지면: id를 직접 넣어야 하고, 넣지 않으면 저장 시 오류가 납니다.
- `Long`(대문자) — 역할: 저장 전 "아직 id 없음"을 `null`로 표현합니다. `long`이면 기본값 0이 "0번 상품"처럼 보입니다. 문법: `Long`은 기본형 `long`을 감싼 Java 래퍼 클래스입니다.
- `protected Product() {}` — 역할: JPA가 DB에서 읽을 때 빈 객체를 먼저 만들고 값을 채웁니다. 빠지면: Hibernate가 객체를 만들 수 없어 조회 시 오류가 납니다. `protected`인 이유: 다른 코드가 이름·가격 없는 빈 상품을 실수로 만들지 못하게 막습니다.
- `setId`가 없음(56줄 주석) — 역할: id를 바꾸는 길 자체를 없앴습니다. 테스트에서 id를 넣어야 할 때는 `ReflectionTestUtils.setField`를 씁니다(5-1).

### 4-4. 조회와 404: Optional과 람다

전체 역할: id로 상품을 찾고, 없으면 404 예외를 던집니다. `update`, `delete`, `updateDescription`, AI 설명 생성이 모두 이 메서드를 재사용합니다. 입력 `Long id`, 출력 `Product` 또는 예외.

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

- `productRepository.findById(id)` — 역할: `Optional<Product>`를 돌려줍니다. 있으면 상품이 든 상자, 없으면 빈 상자입니다. `null`을 돌려주지 않으므로 "없음"을 잊고 넘어가기 어렵습니다. 문법: `Optional`은 JDK 표준 API(`java.util.Optional`)입니다.
- `.orElseThrow(() -> new ...)` — 역할: 비어 있으면 괄호 안 코드로 예외를 만들어 던지고, 있으면 상품을 꺼냅니다. 바뀌면: `.get()`을 쓰면 없을 때 `NoSuchElementException` → 500이 됩니다. 문법: `() -> ...`는 Java 람다(매개변수 없는 함수). 예외 객체는 실제로 비었을 때만 만들어집니다.
- `ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found")` — Spring 라이브러리 예외. `GlobalExceptionHandler`가 `{"status":404,"message":"Product not found"}`로 바꿉니다(Story 4).

### 4-5. 등록과 수정: @Transactional과 변경 감지

전체 역할: `create`는 DTO에서 값을 꺼내 새 Entity를 저장하고, `update`는 기존 Entity의 값만 바꿉니다.

`src/main/java/com/example/product_api/ProductService.java:31–44` (원문)
```java
    // id는 DB가 자동으로 정하므로 중복 검사가 필요 없다
    @Transactional
    public Product create(ProductRequest request) {
        return productRepository.save(new Product(request.getName(), request.getPrice()));
    }

    // @Transactional 안에서 조회한 Entity의 값을 바꾸면, 메서드가 끝날 때 JPA가 UPDATE를 자동 실행한다
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = findById(id);
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        return product;
    }
```

- `new Product(request.getName(), request.getPrice())` — 역할: DTO → Entity 변환. 요청의 `id`는 쓰지 않습니다(애초에 `@Null`로 막혀 있음). 이렇게 나누면: 요청 모양(DTO)과 테이블 모양(Entity)을 따로 바꿀 수 있습니다. 실제로 Story 10에서 Entity에 `description`이 생겼지만 `ProductRequest`는 그대로였습니다.
- `@Transactional` — 역할: 메서드 시작에 트랜잭션을 열고, 정상 종료하면 커밋, `RuntimeException`이 나면 롤백합니다. 문법: Spring 어노테이션. Spring이 이 클래스를 감싼 프록시(대리 객체)를 만들어 메서드 앞뒤에 트랜잭션 코드를 끼워 넣습니다.
- `update`에 `save`가 없는 이유 — 역할: `findById`로 읽은 `product`는 트랜잭션 동안 영속성 컨텍스트가 추적합니다. 커밋 때 처음 값과 비교해 바뀐 부분을 `UPDATE`합니다. `@Transactional`이 빠지면: 조회가 끝나는 순간 추적이 끊겨, `setName`은 Java 객체만 바꾸고 DB는 그대로입니다. 응답은 "New Keyboard"인데 DB는 "Keyboard"인 상태가 됩니다(코드 근거 추론. 통합 테스트 56–58줄이 이 경우를 잡도록 되어 있음).

> 해설: 도서관에서 책을 대출 카드에 적고 빌려 오면(트랜잭션 안 조회), 반납할 때(커밋) 사서가 낙서한 곳을 확인해 기록합니다. 대출 기록 없이 사진만 찍어 온 책(트랜잭션 밖 조회)에 낙서해도 도서관 책은 그대로입니다.

### 4-6. 환경별 설정: 계정은 환경 변수, 테스트는 H2

`src/main/resources/application.properties:3–6` (원문)
```properties
# MySQL 연결 (계정 정보는 환경 변수에서 읽음 - 비밀번호를 이 파일에 적지 말 것)
spring.datasource.url=jdbc:mysql://localhost:3306/product_api
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```
- `${DB_PASSWORD}` — 역할: 실행할 때 환경 변수에서 값을 읽습니다. 파일에는 이름만 있어 저장소(Public)에 비밀번호가 올라가지 않습니다. 문법: Spring 설정의 자리 표시자(placeholder).

`src/test/resources/application.properties:1–7` (원문)
```properties
# 테스트 전용 설정: src/main/resources/application.properties 대신 이 파일이 사용됨
# MySQL이나 환경 변수 없이 메모리 DB(H2)로 테스트
spring.application.name=product-api

spring.datasource.url=jdbc:h2:mem:product_api_test;MODE=MySQL;DB_CLOSE_DELAY=-1
spring.datasource.username=sa
spring.datasource.password=
```
- 1줄 "대신 이 파일이 사용됨" — 역할: 테스트 실행 시 이 파일과 main 파일이 같은 이름(`application.properties`)이라 테스트 쪽이 먼저 발견되고 main 파일은 읽히지 않습니다. 이 파일이 없어지면: main의 MySQL 설정으로 테스트가 돌고, 통합 테스트의 `deleteAll()`이 실제 데이터를 지울 수 있습니다.
- `MODE=MySQL` — H2가 MySQL 문법을 흉내 내게 합니다. 완전히 같지는 않습니다(7절).
- 당시(첫 커밋) 이 파일의 마지막 줄은 `spring.jpa.hibernate.ddl-auto=create-drop`(테스트 시작 때 테이블 생성)이었습니다. Story 10부터 `validate`로 바뀌었고 테이블은 Flyway가 만듭니다.

`pom.xml:83–89` (원문) — `local` 프로필 적용 범위
```xml
				<artifactId>spring-boot-maven-plugin</artifactId>
				<configuration>
					<!-- mvnw spring-boot:run 으로 실행할 때만 local 프로필 적용 (테스트에는 적용되지 않음) -->
					<profiles>
						<profile>local</profile>
					</profiles>
				</configuration>
```
- 역할: `spring-boot:run`일 때만 `application-local.properties`를 추가로 읽습니다. 예전에는 Windows 환경 변수(`SPRING_PROFILES_ACTIVE=local`)로 켜서 테스트에도 적용되는 문제가 있었고, 원인인 전역 환경 변수를 없애고 이 방식으로 바꿨습니다(첫 커밋 README 설계 결정 기록).

## 5. 테스트 코드 읽기

### 5-1. Service 단위 테스트: 가짜 Repository

`src/test/java/com/example/product_api/ProductServiceTest.java:55–70` (원문)
```java
    @Test
    void createSavesNewProductFromRequest() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);   // DB가 id를 정하는 것을 흉내 낸다
            return saved;
        });

        Product created = productService.create(new ProductRequest("Keyboard", 50000));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Keyboard");
        assertThat(captor.getValue().getPrice()).isEqualTo(50000);
        assertThat(created.getId()).isEqualTo(1L);
    }
```
- `when(...).thenAnswer(invocation -> ...)` — 가짜 `save`가 호출되면 받은 객체에 id 1을 넣어 돌려줍니다. DB의 `AUTO_INCREMENT`를 흉내 낸 것입니다. 문법: Mockito API + 람다.
- `ReflectionTestUtils.setField(saved, "id", 1L)` — `setId`가 없으므로 리플렉션(필드 이름으로 직접 접근)으로 값을 넣습니다. Spring 테스트 유틸(라이브러리 API). 운영 코드에서는 쓰지 않습니다.
- `ArgumentCaptor` — Service가 Repository에 **무엇을 넘겼는지** 잡아서 확인합니다. "DTO의 이름·가격이 Entity로 제대로 옮겨졌는가"를 봅니다.

한계: 이 테스트들은 Repository가 가짜라서 실제 SQL과 변경 감지는 확인하지 않습니다. `updateChangesNameAndPriceButKeepsId`(72–81줄)는 Java 객체의 값만 확인합니다.

### 5-2. 통합 테스트: Repository로 다시 조회

`src/test/java/com/example/product_api/ProductApiIntegrationTest.java:49–58` (원문)
```java
        mockMvc.perform(put("/products/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Keyboard\",\"price\":55000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id));

        // 수정 내용이 실제로 DB에 반영됐는지 확인
        Product updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("New Keyboard");
        assertThat(updated.getPrice()).isEqualTo(55000);
```
- 응답(200)만 보지 않고 Repository로 DB를 다시 읽어 확인합니다. 응답은 Java 객체로 만들기 때문에, DB에 반영되지 않아도 응답은 "New Keyboard"일 수 있습니다. 이 확인이 5-1의 한계를 메웁니다.
- `@SpringBootTest`(23줄) — Spring 전체(Controller, Service, 진짜 Repository, H2)를 띄웁니다. 그래서 단위 테스트보다 느립니다.

## 6. 성공과 실패 사례

아래는 테스트 코드에 적힌 실제 입력과 기대값입니다. 이 보고서를 쓰면서 테스트를 실행하지는 않았습니다. 통합 테스트는 H2와 MockMvc를 쓰며 실제 서버·MySQL 결과가 아닙니다.

| # | 입력 | 처리 | 기대 결과 | 테스트 |
|---|---|---|---|---|
| 1 | `POST` `{"name":"Keyboard","price":50000}` → 다시 `GET` | DB가 id 생성, H2에 저장 | 201, 이후 `GET` 200 `name` = `Keyboard`, Repository 조회 성공 | `crudFlowWorksWithDatabase` |
| 2 | `PUT /products/{id}` `{"name":"New Keyboard","price":55000}` | 변경 감지로 UPDATE | 200, DB 값 `New Keyboard` / 55000 | `crudFlowWorksWithDatabase` |
| 3 | `DELETE` 후 같은 id `GET` | 삭제 → 다시 조회 | 204, DB 개수 0, 이후 404 `Product not found` | `crudFlowWorksWithDatabase` |
| 4 | Keyboard, Mouse 연속 등록 | `AUTO_INCREMENT` | 두 id가 다름, 개수 2 | `databaseGeneratesDifferentIds` |
| 5 | `POST` `{"id":5,"name":"Keyboard","price":50000}` | `@Null` 검증 실패 | 400, DB 개수 0 | `invalidRequestDoesNotSaveAnything` |
| 6 | Service에 없는 id 99 삭제 (가짜 Repository는 빈 `Optional`) | `findById`에서 404 | `ResponseStatusException(404)`, `delete` 호출 0번 | `deleteThrowsNotFoundWhenMissing` |

5·6번은 "실패"가 아니라 **의도한 거절이 제대로 일어나야 통과하는 테스트**입니다. 잘못된 요청이 DB까지 가지 않는다는 것을 확인합니다.

설명용 예측(직접 실행하지 않음): 실제 서버에서 Keyboard를 등록하고 서버를 재시작한 뒤 `GET /products`를 보내면 Keyboard가 그대로 있어야 합니다. 첫 커밋 README에 사용자가 이를 확인했다는 기록이 있습니다(과거 기록, 당시 출력 없음).

## 7. 검증 한계와 해야 할 일

- Story 5 직후 코드와 Story 5~6 당시 테스트 실행 결과는 남아 있지 않습니다. 위 표는 현재 테스트 코드의 입력과 기대값입니다.
- H2(`MODE=MySQL`)는 MySQL과 완전히 같지 않습니다. 정렬 규칙이나 일부 SQL 함수가 다를 수 있습니다. 실제 MySQL 동작은 서버를 띄워 확인해야 합니다.
- 직접 확인: `.\mvnw.cmd spring-boot:run` → `api.http`로 상품 등록 → 서버 종료 후 재실행 → `GET /products`로 남아 있는지 확인. MySQL에서 `SELECT * FROM products;`로 직접 볼 수도 있습니다. 확인용으로 만든 상품은 같은 파일의 DELETE 요청으로 그 상품만 지웁니다.

## 8. 이해도 확인 질문

**Q1.** `ProductController`의 생성자를 지우고 필드를 `@Autowired private ProductService productService;`로 바꾸면, 서버 동작과 `ProductControllerTest`는 어떻게 될까요?
> 해설: 서버는 그대로 동작합니다. Spring이 필드에 직접 넣어 주기 때문입니다. 하지만 `ProductControllerTest.java:41`의 `new ProductController(productService)`는 그런 생성자가 없어 컴파일되지 않습니다. 또 `final`을 쓸 수 없어 필드가 비어 있는 상태로 객체가 만들어질 수 있습니다. 생성자 주입은 "필요한 것이 없으면 객체를 만들 수 없다"를 컴파일러가 보장해 줍니다.

**Q2.** `ProductService.update`에서 `@Transactional`만 지우면, `PUT /products/1` `{"name":"New Keyboard","price":55000}`의 응답과 DB는 각각 어떻게 될까요?
> 해설: 응답은 200 `New Keyboard`로 정상처럼 보입니다. 응답은 메모리의 Java 객체로 만들기 때문입니다. 하지만 조회 직후 JPA의 추적이 끊겨 `UPDATE`가 실행되지 않으므로 DB는 `Keyboard`로 남습니다. 그래서 통합 테스트는 응답만 보지 않고 Repository로 다시 조회합니다(`ProductApiIntegrationTest.java:56–58`). 이 경우를 실제로 실행해 보지는 않았습니다(코드 근거 추론).

**Q3.** `src/test/resources/application.properties`를 실수로 삭제하고 `.\mvnw.cmd test`를 실행하면 어떤 일이 생길 수 있을까요?
> 해설: 테스트가 main의 `application.properties`를 읽어 실제 MySQL에 연결하려 합니다. 환경 변수가 없으면 연결 실패로 끝나지만, 환경 변수가 설정된 PC라면 `ProductApiIntegrationTest`의 `deleteAll()`(37줄)이 실제 상품 데이터를 지울 수 있습니다. 이 파일이 "테스트 환경 분리"의 핵심이라 CLAUDE.md에서도 삭제를 금지합니다.
