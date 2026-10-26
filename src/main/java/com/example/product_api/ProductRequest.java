package com.example.product_api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

// POST/PUT 요청 본문 전용 클래스 (DB와 연결되지 않음)
public class ProductRequest {

    // id는 DB가 정하므로 클라이언트가 보내면 400으로 거절한다
    @Null(message = "id must not be provided")
    private Long id;

    @NotBlank(message = "name must not be blank")
    private String name;

    // int는 값이 빠지면 0이 되어 @Min(0)을 통과하므로, 누락을 잡기 위해 Integer + @NotNull 사용
    @NotNull(message = "price must not be null")
    @Min(value = 0, message = "price must be greater than or equal to 0")
    private Integer price;

    public ProductRequest() {

    }

    public ProductRequest(String name, Integer price) {
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getPrice() {
        return price;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }
}
