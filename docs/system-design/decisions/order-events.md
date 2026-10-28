# 주문 확정 사실의 직접 발행 판단

기존 절 번호 참조: [2.11](../features/order-events/publication.md#211-주문-확정-이벤트-발행의-책임과-설정) · [3.7](../features/order-events/README.md#37-최초-pg-확정-시각과-사건-값) · [4.6](../features/order-events/publication.md#46-주문-확정-이벤트-발행).

기존 문서의 계약·절 번호·승인/검증 상태를 보존해 이동했다. 재편 자체는 제품 계약 변경이나 새 실행 검증을 뜻하지 않는다.

## A.34 주문 확정 사실의 직접 발행

**상태·근거 구분:** 2026-10-28 사용자와 사건 ID·확정 시각·AFTER_COMMIT·비동기 결과 관측·로그·producer 내부 재시도·유한 종료 대기 정책을 합의하고 발행 측 상세설계 작성을 승인받았다. 2.11·3.7·4.6·5.8은 그 정책의 상세 초안이며 전체 설계 최종 승인·구현·실험 결과가 아니다. GPT 검토는 제공한 코드 요약에 대한 제안으로 검토했고 사용자의 선택을 대신하지 않았다. 원문 요구는 최초 PG 확정 뒤 직접 발행·구매 활동 저장·두 공백 검증이며 Outbox·랭킹 API는 이번 주 제외다.

### 문제·제약·선택과 비용

|판단|선택·이유|대안이 유리한 조건·포기한 것|
|---|---|---|
|주문과 구매 활동의 책임|주문은 확정 사실만 전달. 후속 저장을 동기 업무 호출하지 않음|직접 호출은 완료 흐름이 명확하지만 주문이 후속 장애·변경을 함께 알아야 함. 이벤트는 전달·지연·관측 책임을 새로 만듦|
|사건 식별|`order-confirmed:{orderId}` 유도. 주문당 최초 확정 하나라 별도 저장 이익이 작음|UUID 저장은 사건 식별 규칙을 주문 ID에서 분리할 수 있지만 열·생성·정합성 비용 추가. 재확정 요구가 생기면 유도 전제를 재검토|
|ID와 스키마|schemaVersion은 wire에만. 같은 사실의 표현 변경으로 ID를 바꾸지 않음|버전 포함 ID도 가능하나 schemaVersion 변경과 사건 신원 변경을 혼동할 비용. 현재 v1 밖의 호환 정책은 미구현|
|시각|최초 CONFIRM에서 UTC·마이크로초 시각을 한 번 생성해 저장·snapshot 공유. 기존 ZonedDateTime 유지|Instant로 바꿀 필수 이유가 없고 불필요한 타입 전환을 피함. DATETIME(6) 목표와 실제 ORM/JDBC 왕복을 검증해야 함|
|발행 연결|Spring AFTER_COMMIT listener. snapshot은 TX 안에서 생성|기존 AfterCommitRunner 재사용은 작은 변경, 반환 뒤 orchestration은 순서가 명시적. listener는 사건과 전송 책임을 분리하지만 암묵적 호출 추적·TX 밖 등록 무시를 알아야 함|
|내부/외부 모델|domain 불변 사건·domain 포트, infrastructure wire DTO·Kafka adapter|동일 DTO 공유는 타입 수가 줄지만 내부 사건이 외부 JSON 버전과 결합. 분리안은 얇은 변환을 유지하는 비용|
|비동기 결과 관측|send 뒤 Future만 관측, 별도 @Async 없음|동기 완료 대기는 응답 전 broker 결과를 확인할 기회가 있지만 요청 지연을 늘리며 소비 완료·DB/broker 원자성은 못 줌. @Async는 호출 자체를 옮길 수 있으나 executor 거부·큐·종료 공백 추가|
|재시도·관측|producer 내부 재시도와 로그만. 자체 재발행 없음|영속 재발행·Outbox는 commit→send 공백 복구에 유리하지만 새로운 상태·수명·중복 처리가 필요해 범위 제외. 재발행 일반이 모두 Outbox라는 뜻은 아님|
|설정 격리|이름 붙인 전용 factory/template, 공통 props 복사 뒤 retries 상속 제거|공통 producer 설정 일괄 변경은 간단하지만 consumer·다른 발행 경로까지 영향. 전용 bean·namespace·qualifier를 검증하는 비용 수용|
|정상 종료|HTTP graceful와 producer 15초 close, phase 대기 30초|더 긴 close는 정상 전달 기회를 더 주지만 종료가 길어짐. 30초 전달 예산을 종료 때 모두 주지 않는 비용을 수용|

**중간 검토 후 추가 합의:** 공통 설정 복사·전용 override 이후 `transactional.id`가 있거나 전용 factory가 transaction-capable이면 기동 오류로 거절한다. 비트랜잭션 발행이라는 기존 선택을 조용히 바꾸지 않기 위해서다. 충돌 설정을 제거하고 실행하는 대안은 기동을 이어갈 수 있지만 설정 오류를 숨길 비용이 있어 채택하지 않았다. 현재 공통 설정에 실제로 충돌이 발견됐다는 뜻은 아니며 2.11·P5에서 확인할 조건이다. 품목의 합산 전 검증·독립 DB 연결의 commit 관측·모든 partition의 누락 검사도 기존 계약을 명확히 하는 보완이며 새 전달 보장을 추가한 것은 아니다.

초기 제안의 max.block=1초·request=3초·delivery=10초·retries=3은 빠른 실패 관측을 우선한 안이었다. 그러나 응답시간 요구·실측이 없는 상태에서 정상 최초 metadata 확보와 일시 지연까지 조기에 포기할 근거가 부족했다. 사용자는 max.block=5초·기존 request=20초·delivery=30초·retries 기본값으로 시간 예산을 우선하는 수정안에 동의했다. 2.11이 현재 초기값의 단일 기준이며 이전 수치는 구현값이 아니다. 수정안도 실측된 최적값은 아니고 실제 cold start·오류·종료 관찰로 재검토한다.

### 공식·코드 근거와 입증하지 않은 것

- **공식 소스 확인:** 로컬 Kafka clients 3.8.1의 ProducerConfig는 idempotence의 acks=all·retries>0·in-flight<=5 조건, delivery/request/linger 관계, metadata·buffer만 다루는 max.block 설명, 횟수보다 delivery timeout 중심 제어 권고를 포함한다. CommonClientConfigs는 backoff·jitter를 설명한다. KafkaProducer.close(Duration)는 종료 대기 후 미완료 전송 강제 종료 경로를 가진다. 설정 의미·구현 읽기이며 실제 runtime의 동작 확인은 아니다.
- **공식 소스 확인:** 로컬 Spring Kafka 3.3.4 DefaultKafkaProducerFactory의 setPhysicalCloseTimeout·getPhase·stop·destroy 경로를 읽었다. 기본 구조를 근거로 HTTP 정리 뒤 producer 종료를 설계하지만 실제 bean 순서·스레드 정리는 4.6에서 확인한다. Spring Framework 6.2의 TransactionalEventListener·TransactionPhase 계약에 따라 commit 성공 후 처리·fallbackExecution=false를 명시한다. 구체 버전의 afterCompletion 예외 처리에만 기대지 않고 서비스 로그·명시적 동기 오류 처리로 정책을 표현한다.
- **코드 확인:** 현재 PG는 공통 applier·TX B·Order 잠금·CONFIRM/FINAL_UNCHANGED를 구분한다. 기존 OrderModel에 confirmedAt은 없고 품목은 LAZY·생성 뒤 고정이다. 공통 YAML은 retries=3·request timeout=20초·backoff=500ms·type headers=false다. 공통 factory는 KafkaProperties에서 map을 만든다. YAML 선언은 실제 적용 증거가 아니다.
- **코드/공식 매핑 기준:** 기존 JPA의 NORMALIZE_UTC·jdbc.time_zone=UTC와 ZonedDateTime을 유지한다. MySQL 8.0 Reference Manual의 Fractional Seconds in Time Values가 설명하는 마이크로초 저장 정밀도를 목표로 삼지만 실제 DATETIME(6) 생성·왕복·JSON은 미검증이다. 로컬 캐시 jar 버전·Docker image 선언은 실행 중인 의존성·서버 버전 확인을 대신하지 않는다.
- **사용자 제공 사실:** 보존할 실데이터가 없어 이번 신규 컬럼의 이관·backfill은 제외한다. DB 조회 결과가 아니며 유효한 POINT·PG 보존 테스트의 제거 근거도 아니다.

### 남은 한계와 재검토 조건

DB commit→send 사이에는 여전히 미발행 공백이 있다. idempotent producer·안정 ID·정상 종료 대기·동기 완료 대기 어느 것도 이를 제거하지 않는다. broker에 같은 사건의 별도 record가 생길 수 있고 구매 효과의 유일성은 소비 DB 계약이 맡아야 한다. timeout이나 실패 Future는 broker 미기록의 일반 증거가 아니다. 로그만으로 자동 복구나 완전한 관측도 보장하지 않는다.

재확정·멀티테넌트 ID 공간·다중 broker·더 강한 HTTP 응답시간/복구 보장·민감 payload·consumer 스키마 진화 요구가 생기면 관련 판단을 다시 검토한다. 실제 cold start 실패·요청 지연·종료 중 미완료·DB/payload 시각 차이가 관측되면 먼저 유효 설정·실행 경계를 확인하고, 계약 변경이 필요하면 사용자에게 판단받는다. 모든 검토를 추가 실험으로 확장하지 않으며 선택적 글감 실험은 주제 선정 후 별도 승인한다.
