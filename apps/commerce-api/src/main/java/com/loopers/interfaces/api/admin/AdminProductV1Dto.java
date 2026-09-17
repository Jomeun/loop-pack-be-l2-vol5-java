package com.loopers.interfaces.api.admin;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductQueryResult;

public class AdminProductV1Dto {

    public record ProductCreateRequest(Long brandId, String name, Long price) {
    }

    public record ProductUpdateRequest(String name, Long price) {
    }

    public record StockUpdateRequest(Long quantity) {
    }

    /** 관리자 상품 응답에는 좋아요 수를 포함하지 않는다. */
    public record AdminProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        long price,
        long stockQuantity
    ) {
        public static AdminProductResponse from(ProductQueryResult result) {
            return new AdminProductResponse(
                result.id(),
                result.brandId(),
                result.brandName(),
                result.name(),
                result.price(),
                result.stockQuantity()
            );
        }
    }

    public record StockResponse(Long productId, long quantity) {
        public static StockResponse from(ProductModel product) {
            return new StockResponse(product.getId(), product.getStockQuantity());
        }
    }
}
