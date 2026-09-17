package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductQueryResult;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        long price,
        long likeCount,
        long stockQuantity
    ) {
        public static ProductResponse from(ProductQueryResult result) {
            return new ProductResponse(
                result.id(),
                result.brandId(),
                result.brandName(),
                result.name(),
                result.price(),
                result.likeCount(),
                result.stockQuantity()
            );
        }
    }
}
