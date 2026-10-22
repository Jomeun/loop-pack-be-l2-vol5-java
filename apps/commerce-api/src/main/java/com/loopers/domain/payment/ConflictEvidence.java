package com.loopers.domain.payment;

/**
 * 파싱한 충돌 근거. 충돌 요약의 observed_* 열과 같은 범위만 담고 원문·카드는 담지 않는다.
 * 복수 거래는 건수만 남기며 임의 거래의 키·금액·상태를 대표값으로 고르지 않는다.
 */
public record ConflictEvidence(
    String observedOrderId,
    String observedTransactionKey,
    Long observedAmount,
    PgTransactionStatus observedStatus,
    Integer observedCount
) {

    public static ConflictEvidence of(PaymentObservation.TransactionObserved transaction) {
        return new ConflictEvidence(transaction.providerOrderId(), transaction.transactionKey(), transaction.amount(),
            transaction.status(), null);
    }

    public static ConflictEvidence ofCount(int count) {
        if (count < 2) {
            throw new IllegalArgumentException("복수 거래 건수는 2 이상");
        }
        return new ConflictEvidence(null, null, null, null, count);
    }
}
