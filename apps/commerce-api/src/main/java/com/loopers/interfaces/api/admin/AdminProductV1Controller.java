package com.loopers.interfaces.api.admin;

import com.loopers.domain.common.ListSort;
import com.loopers.domain.common.PageCommand;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductQueryResult;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller implements AdminProductV1ApiSpec {

    private final ProductService productService;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<AdminProductV1Dto.AdminProductResponse>> getProducts(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "sort", required = false) String sort
    ) {
        PageResult<ProductQueryResult> result =
            productService.getAllProducts(PageCommand.of(page, size), ListSort.from(sort));
        return ApiResponse.success(PageResponse.of(result, AdminProductV1Dto.AdminProductResponse::from));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<AdminProductV1Dto.AdminProductResponse>> create(
        @RequestBody(required = false) AdminProductV1Dto.ProductCreateRequest request
    ) {
        AdminProductV1Dto.ProductCreateRequest body = request != null
            ? request
            : new AdminProductV1Dto.ProductCreateRequest(null, null, null);
        if (body.brandId() == null) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }

        ProductModel created = productService.create(body.brandId(), body.name(), priceOf(body.price()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(detailOf(created.getId())));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.AdminProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        return ApiResponse.success(detailOf(productId));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<AdminProductV1Dto.AdminProductResponse> update(
        @PathVariable(value = "productId") Long productId,
        @RequestBody(required = false) AdminProductV1Dto.ProductUpdateRequest request
    ) {
        AdminProductV1Dto.ProductUpdateRequest body = request != null
            ? request
            : new AdminProductV1Dto.ProductUpdateRequest(null, null);
        productService.update(productId, body.name(), priceOf(body.price()));
        return ApiResponse.success(detailOf(productId));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Object> delete(@PathVariable(value = "productId") Long productId) {
        productService.delete(productId);
        return ApiResponse.success();
    }

    @PutMapping("/{productId}/stock")
    @Override
    public ApiResponse<AdminProductV1Dto.StockResponse> changeStock(
        @PathVariable(value = "productId") Long productId,
        @RequestBody(required = false) AdminProductV1Dto.StockUpdateRequest request
    ) {
        if (request == null || request.quantity() == null) {
            throw new CoreException(ErrorType.INVALID_STOCK_QUANTITY);
        }

        ProductModel changed = productService.changeStock(productId, request.quantity());
        return ApiResponse.success(AdminProductV1Dto.StockResponse.from(changed));
    }

    private AdminProductV1Dto.AdminProductResponse detailOf(Long productId) {
        return AdminProductV1Dto.AdminProductResponse.from(productService.getProduct(productId));
    }

    /** price 가 없는 요청도 상품 가격 규칙으로 판단한다. */
    private static long priceOf(Long price) {
        if (price == null) {
            throw new CoreException(ErrorType.INVALID_PRODUCT_PRICE);
        }
        return price;
    }
}
