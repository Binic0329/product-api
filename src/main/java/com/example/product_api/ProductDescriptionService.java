package com.example.product_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(ProductDescriptionService.class);

    private final ProductService productService;
    private final AiDescriptionClient aiDescriptionClient;

    public ProductDescriptionService(ProductService productService, AiDescriptionClient aiDescriptionClient) {
        this.productService = productService;
        this.aiDescriptionClient = aiDescriptionClient;
    }

    // AI 호출은 느릴 수 있어서, DB 트랜잭션을 잡은 채 기다리지 않도록 @Transactional을 붙이지 않는다
    public ProductDescriptionResponse generate(Long id) {
        Product product = productService.findById(id);   // 없으면 404, AI는 호출하지 않음

        String description;
        try {
            description = aiDescriptionClient.generate(product.getName(), product.getPrice());
        } catch (AiTimeoutException ex) {
            // 원인은 서버 로그에만 남기고 응답에는 고정 메시지만 보낸다
            log.warn("AI description timed out (productId={})", id, ex);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI service timeout");
        } catch (AiClientException ex) {
            log.warn("AI description failed (productId={})", id, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service error");
        }

        // AI가 응답은 했지만 쓸 수 있는 내용이 없으면 502
        if (description == null || description.isBlank()) {
            log.warn("AI returned blank description (productId={})", id);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service error");
        }
        return new ProductDescriptionResponse(product.getId(), description);
    }
}
