package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order V1 API", description = "관리자 주문 조회 API")
public interface AdminOrderV1ApiSpec {

    ApiResponse<PageResponse<AdminOrderV1Dto.AdminOrderResponse>> getOrders(Integer page, Integer size, String sort);

    ApiResponse<AdminOrderV1Dto.AdminOrderResponse> getOrder(Long orderId);
}
