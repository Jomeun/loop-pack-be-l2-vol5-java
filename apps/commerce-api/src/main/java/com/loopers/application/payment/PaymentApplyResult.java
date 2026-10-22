package com.loopers.application.payment;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.payment.PaymentConflictCode;

/**
 * 결과 반영 TX 의 결과(설계 2.7). outcome 은 HTTP 코드가 아니며 호출자가 commit 뒤 응답으로 바꾼다.
 * conflictCode 는 CONFLICT 일 때 저장한 요약 코드다.
 */
public record PaymentApplyResult(OrderInfo order, Outcome outcome, PaymentConflictCode conflictCode) {

    public enum Outcome {
        FINAL,
        PENDING,
        UNAVAILABLE,
        CONFLICT
    }
}
