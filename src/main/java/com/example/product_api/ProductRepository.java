package com.example.product_api;

import org.springframework.data.jpa.repository.JpaRepository;

// 인터페이스만 선언하면 Spring Data JPA가 findAll, findById, save, delete 등을 자동으로 구현해 준다
public interface ProductRepository extends JpaRepository<Product, Long> {
}
