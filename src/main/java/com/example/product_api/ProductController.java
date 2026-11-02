package com.example.product_api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // 응답은 Entity 대신 ProductResponse로 바꿔서 돌려준다
    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.findAll().stream()
            .map(ProductResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.create(request));
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(
        @PathVariable Long id,
        @Valid @RequestBody ProductRequest request
    ) {
        return ProductResponse.from(productService.update(id, request));
    }

    // 사람이 검토·수정한 설명을 저장한다. AI 초안 생성(generate-description)은 저장하지 않는다
    @PutMapping("/{id}/description")
    public ProductResponse updateDescription(
        @PathVariable Long id,
        @Valid @RequestBody ProductDescriptionRequest request
    ) {
        return ProductResponse.from(productService.updateDescription(id, request.getDescription()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        productService.delete(id);
    }
}
