package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Product V1 API", description = "관리자 상품·재고 API")
public interface AdminProductV1ApiSpec {

    ApiResponse<PageResponse<AdminProductV1Dto.AdminProductResponse>> getProducts(
        Integer page, Integer size, String sort);

    ResponseEntity<ApiResponse<AdminProductV1Dto.AdminProductResponse>> create(
        AdminProductV1Dto.ProductCreateRequest request);

    ApiResponse<AdminProductV1Dto.AdminProductResponse> getProduct(Long productId);

    ApiResponse<AdminProductV1Dto.AdminProductResponse> update(
        Long productId, AdminProductV1Dto.ProductUpdateRequest request);

    ApiResponse<Object> delete(Long productId);

    ApiResponse<AdminProductV1Dto.StockResponse> changeStock(
        Long productId, AdminProductV1Dto.StockUpdateRequest request);
}
