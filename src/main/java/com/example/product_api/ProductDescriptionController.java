package com.example.product_api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 기존 ProductController와 분리해, 상품 CRUD 코드와 테스트를 건드리지 않고 AI 기능을 추가한다
@RestController
@RequestMapping("/products")
public class ProductDescriptionController {

    private final ProductDescriptionService productDescriptionService;

    public ProductDescriptionController(ProductDescriptionService productDescriptionService) {
        this.productDescriptionService = productDescriptionService;
    }

    // 설명을 만들어 응답만 하고 DB에는 저장하지 않으므로 201이 아닌 200
    @PostMapping("/{id}/generate-description")
    public ProductDescriptionResponse generateDescription(@PathVariable Long id) {
        return productDescriptionService.generate(id);
    }
}
