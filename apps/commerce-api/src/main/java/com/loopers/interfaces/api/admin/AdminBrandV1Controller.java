package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.ListSort;
import com.loopers.domain.common.PageCommand;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandV1Controller implements AdminBrandV1ApiSpec {

    private final BrandService brandService;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<AdminBrandV1Dto.AdminBrandResponse>> getBrands(
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "sort", required = false) String sort
    ) {
        PageResult<BrandModel> result = brandService.getBrands(PageCommand.of(page, size), ListSort.from(sort));
        return ApiResponse.success(PageResponse.of(result, AdminBrandV1Dto.AdminBrandResponse::from));
    }

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<AdminBrandV1Dto.AdminBrandResponse>> create(
        @RequestBody(required = false) AdminBrandV1Dto.BrandSaveRequest request
    ) {
        BrandModel created = brandService.create(nameOf(request));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(AdminBrandV1Dto.AdminBrandResponse.from(created)));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandV1Dto.AdminBrandResponse> getBrand(@PathVariable(value = "brandId") Long brandId) {
        return ApiResponse.success(AdminBrandV1Dto.AdminBrandResponse.from(brandService.getBrand(brandId)));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandV1Dto.AdminBrandResponse> update(
        @PathVariable(value = "brandId") Long brandId,
        @RequestBody(required = false) AdminBrandV1Dto.BrandSaveRequest request
    ) {
        BrandModel updated = brandService.update(brandId, nameOf(request));
        return ApiResponse.success(AdminBrandV1Dto.AdminBrandResponse.from(updated));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Object> delete(@PathVariable(value = "brandId") Long brandId) {
        brandService.delete(brandId);
        return ApiResponse.success();
    }

    /** 본문이나 name 이 없는 요청도 브랜드명 규칙으로 판단한다. */
    private static String nameOf(AdminBrandV1Dto.BrandSaveRequest request) {
        return request != null ? request.name() : null;
    }
}
