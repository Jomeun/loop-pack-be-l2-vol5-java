package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Brand V1 API", description = "관리자 브랜드 API")
public interface AdminBrandV1ApiSpec {

    ApiResponse<PageResponse<AdminBrandV1Dto.AdminBrandResponse>> getBrands(Integer page, Integer size, String sort);

    ResponseEntity<ApiResponse<AdminBrandV1Dto.AdminBrandResponse>> create(AdminBrandV1Dto.BrandSaveRequest request);

    ApiResponse<AdminBrandV1Dto.AdminBrandResponse> getBrand(Long brandId);

    ApiResponse<AdminBrandV1Dto.AdminBrandResponse> update(Long brandId, AdminBrandV1Dto.BrandSaveRequest request);

    ApiResponse<Object> delete(Long brandId);
}
