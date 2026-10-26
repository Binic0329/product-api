package com.example.product_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // 실제 DB 대신 Mockito로 만든 가짜 Repository를 넣어 Service 규칙만 검증한다
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
    }

    @Test
    void findAllReturnsProductsFromRepository() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "Keyboard", 50000)));

        assertThat(productService.findAll()).hasSize(1);
    }

    @Test
    void findByIdReturnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Keyboard", 50000)));

        assertThat(productService.findById(1L).getName()).isEqualTo("Keyboard");
    }

    @Test
    void findByIdThrowsNotFoundWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

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

    @Test
    void updateChangesNameAndPriceButKeepsId() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1L, "Keyboard", 50000)));

        Product updated = productService.update(1L, new ProductRequest("New Keyboard", 55000));

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getName()).isEqualTo("New Keyboard");
        assertThat(updated.getPrice()).isEqualTo(55000);
    }

    @Test
    void updateThrowsNotFoundWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, new ProductRequest("Ghost", 1000)))
            .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteRemovesProduct() {
        Product keyboard = product(1L, "Keyboard", 50000);
        when(productRepository.findById(1L)).thenReturn(Optional.of(keyboard));

        productService.delete(1L);

        verify(productRepository).delete(keyboard);
    }

    @Test
    void deleteThrowsNotFoundWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
            .satisfies(ex -> assertStatus(ex, HttpStatus.NOT_FOUND));
        verify(productRepository, never()).delete(any());
    }

    // DB에서 읽어 온 것처럼 id가 채워진 Product를 만든다
    private Product product(Long id, String name, Integer price) {
        Product product = new Product(name, price);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private void assertStatus(Throwable ex, HttpStatus expected) {
        assertThat(ex).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(expected);
    }
}
