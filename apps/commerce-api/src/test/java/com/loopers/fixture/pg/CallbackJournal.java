package com.loopers.fixture.pg;

import com.loopers.pgsimulator.CallbackFailure;
import com.loopers.pgsimulator.TransactionSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제로 보낸(sent=true) 알림을 전송 결과가 나온 뒤 기록한다. 그래서 entry 수가 callbackSent 이며, 진행 중인 전송은
 * 아직 세지 않는다(2026-10-25 사용자 결정, 5.7). 전송은 PG 의 콜백 전송기가 하므로 시작 시각 대신 제출 시각을 남긴다.
 */
final class CallbackJournal {

    private final Clock clock;
    private final List<CallbackRecord> records = new ArrayList<>();
    private long nextSequence = 1;

    CallbackJournal(Clock clock) {
        this.clock = clock;
    }

    Instant now() {
        return clock.instant();
    }

    synchronized void record(TransactionSnapshot snapshot, Instant submittedAt, Integer httpStatus,
                             CallbackFailure failure) {
        records.add(new CallbackRecord(nextSequence++, snapshot.transactionKey(), snapshot.orderId(), snapshot.status(),
            submittedAt, clock.instant(), httpStatus, failure));
    }

    synchronized List<CallbackRecord> snapshot() {
        return List.copyOf(records);
    }

    synchronized void clear() {
        records.clear();
    }
}
