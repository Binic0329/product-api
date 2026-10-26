package com.example.product_api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Product not found"
            ));
    }

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

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        productRepository.delete(product);
    }
}
