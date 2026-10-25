package com.loopers.fixture.pg;

import com.loopers.pgsimulator.CallbackFailure;
import com.loopers.pgsimulator.PaymentStatus;

import java.time.Instant;

/** 실제로 보낸 알림 1건의 관측(결과가 나온 뒤 기록). 콜백 URL·요청 원문은 노출하지 않는다. startedAt 은 전송 제출 시각이다. */
public record CallbackRecord(
    long sequence,
    String transactionKey,
    String providerOrderId,
    PaymentStatus status,
    Instant startedAt,
    Instant completedAt,
    Integer httpStatus,
    CallbackFailure failure
) {
}
