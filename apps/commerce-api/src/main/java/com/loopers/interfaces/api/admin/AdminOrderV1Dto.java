package com.loopers.interfaces.api.admin;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderModel;
import com.loopers.interfaces.api.order.OrderV1Dto;

import java.util.List;

public class AdminOrderV1Dto {

    /** 고객 주문 응답에 구매자 식별자를 더한 형태다. */
    public record AdminOrderResponse(
        Long id,
        Long userId,
        String status,
        Long orderTotal,
        Long usedPointAmount,
        Long paymentAmount,
        List<OrderV1Dto.OrderItemResponse> items
    ) {
        public static AdminOrderResponse from(OrderModel order) {
            Money paymentAmount = order.getPaymentAmount();
            return new AdminOrderResponse(
                order.getId(),
                order.getUserId(),
                order.getStatus().name(),
                order.getOrderTotal().toWon(),
                order.getUsedPointAmount(),
                paymentAmount != null ? paymentAmount.toWon() : null,
                order.getItems().stream().map(OrderV1Dto.OrderItemResponse::from).toList()
            );
        }
    }
}
