package com.example.product_api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// DB의 products 테이블 한 줄(row)과 연결되는 클래스
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

    // 사람이 검토한 AI 상품 설명 (Story 10). 상품 등록·수정으로는 바뀌지 않고 설명 저장 API로만 바뀐다
    @Column(length = 1000)
    private String description;

    // JPA가 DB에서 읽어 온 값으로 객체를 만들 때 사용하는 기본 생성자
    protected Product() {

    }

    public Product(String name, Integer price) {
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

    public String getDescription() {
        return description;
    }

    // id는 DB가 정하므로 setId는 두지 않는다
    public void setName(String name) {
        this.name = name;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
