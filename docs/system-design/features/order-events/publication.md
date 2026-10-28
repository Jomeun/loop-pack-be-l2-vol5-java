# 주문 확정 이벤트 발행 상세설계

기존 절 번호 참조: [2.3](../../common/architecture.md#23-허용하는-의존-방향) · [3.7](README.md#37-최초-pg-확정-시각과-사건-값) · [5.8](README.md#58-orderconfirmed-v1-이벤트-계약).

기존 문서의 계약·절 번호·승인/검증 상태를 보존해 이동했다. 재편 자체는 제품 계약 변경이나 새 실행 검증을 뜻하지 않는다.

**상태:** 발행 측 상세설계는 사용자 검토 전 초안이다. 소비 측 상세설계·전체 최종 승인·구현·실행 검증은 미완료이며 이번 이동에서 소비 계약을 새로 작성하지 않는다.

2026-10-28 GPT 중간 검토 후 사용자가 설정 거절 정책·최소 문서 보완을 승인했다. 아래에 해당 내용을 반영했으며 전체 최종 승인·구현 검증과는 구분한다.

## 2.11 주문 확정 이벤트 발행의 책임과 설정

**상태:** 2026-10-28 합의한 발행 정책을 구체화한 상세설계 초안. 사용자 세부 검토·전체 설계 승인·구현·실행 검증은 미완료다. 이 절은 발행 측만 다루며 소비 측 저장·ack 상세설계를 대신하지 않는다. 근거·대안은 [A.34](../../decisions/order-events.md#a34-주문-확정-사실의-직접-발행), 처리 순서는 [4.6](#46-주문-확정-이벤트-발행)이다.

### 클래스와 의존 경계

아래 신규 타입은 `apps/commerce-api` 안에 둔다. 다른 앱·공통 JPA 모듈에 주문 Entity를 옮기거나 공유 이벤트 라이브러리를 새로 만들지 않는다. 기존 2.3 의존 규칙을 유지한다.

|위치·타입|계약과 책임|
|---|---|
|`domain/order/OrderModel`|`confirmPg(Money amount, ZonedDateTime confirmedAt)`으로 최초 PG 확정·시각 기록을 원자적으로 수행. 상세 불변조건은 3.7|
|`domain/order/OrderConfirmed`|불변 사건 record. `from(OrderModel)`은 TX 안에서만 호출하여 `orderId`, UTC `confirmedAt`, 정규화한 불변 `List<Item>` 복사. `Item(long productId, long quantity)`도 record. `eventId()`는 주문 ID에서 결정적으로 유도. Spring·Kafka·JPA Entity를 필드로 보관하지 않음|
|`domain/order/OrderEventPublisher`|`void publish(OrderConfirmed event)`: 최초 확정 사실을 외부에 전달하는 포트. Kafka 타입·Future·broker metadata를 호출자에게 노출하지 않음. 반환은 전송 완료 보장이 아님|
|`application/payment/PaymentTransactionService`|최초 CONFIRM 분기에 확정 시각 생성·`confirmPg`·사건 복사·Spring `ApplicationEventPublisher.publishEvent` 등록을 추가. 기존 잠금·PG 판정·저장·반환 순서 유지|
|`application/order/OrderConfirmedEventListener`|`void on(OrderConfirmed event)`. `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=false)`. probe 뒤 발행 포트를 한 번 호출. `@Async`·`@Transactional`·Entity 조회 없음. commit 뒤 동기 호출 오류는 안전한 로그로 표시하고 정상 반환|
|`application/order/OrderPublicationProbe`|`void beforeSend(OrderConfirmed event)`: commit 뒤·send 전 누락 검증용 seam. 기본 bean은 no-op, 테스트만 대상 주문에서 gate·실패를 주입. 공개 HTTP 제어 기능이나 재발행 기능이 아님|
|`infrastructure/order/KafkaOrderEventPublisher`|발행 포트 구현. `publish`에서 wire DTO 변환·String key·send·Future 완료 로그. nested 불변 `OrderConfirmedV1`·`Item` DTO는 5.8 wire 필드만 가짐. domain·application에 DTO/serializer를 노출하지 않음|
|`config/OrderEventKafkaProperties`|`commerce.order-events` 설정 바인딩. `topic` 기본 `order-events`, `producerProperties`는 아래 표의 설정 map, `physicalCloseTimeoutSeconds` 기본 15. topic 공백·close 음수는 기동 오류. 테스트에서 topic만 고유 suffix로 대체 가능|
|`config/OrderEventKafkaConfig`|이름 `orderEventProducerFactory`, `orderEventKafkaTemplate`인 전용 bean. publisher는 qualifier로 전용 template을 주입. `Clock` bean `orderConfirmationClock`은 `Clock.systemUTC()`, TX 담당 객체도 qualifier로 사용|

`OrderConfirmed`는 외부 소비자의 작업 지시가 아니라 도메인의 확정 사실 값이다. 내부 사건에는 schemaVersion·topic·Kafka key 설정을 넣지 않고 adapter가 5.8 wire 계약을 만든다. 같은 사실을 담더라도 내부 record와 외부 DTO를 분리하여 infrastructure → application 의존을 만들지 않는다. 새 Domain Service나 다른 Facade 호출을 추가하지 않는다. API의 `OrderInfo`와 HTTP DTO에 confirmedAt·발행 상태 필드를 추가하지 않는다.

### 전용 producer의 조립과 적용 범위

commerce-api build에 `modules:kafka` 의존성과 `kafka.yml` import를 연결한다. 공통 `KafkaConfig`의 batch listener·기존 `producerFactory`·`kafkaTemplate`은 전역 변경하지 않는다. 전용 template·factory를 별도로 이름 붙여 주문 발행에만 사용하며, 공통 producer는 사용 전 실제 KafkaProducer를 만들지 않는 기존 lazy 생성 규칙을 확인한다. bean을 만든 사실만으로 연결·발행 성공을 기록하지 않는다.

복수 factory/template이 등록되므로 전용 publisher뿐 아니라 기존 공통 bean의 주입 지점도 확인한다. generic 타입·bean 이름·qualifier로 각 template이 의도한 factory에 연결되는지 전체 컨텍스트 기동 검증에서 확인한다. 모호성을 해결하기 위해 전용 bean을 무조건 `@Primary`로 지정해 기존 경로를 바꾸지 않는다. 실제 충돌이 있으면 기존 공통 연결을 명시적으로 지정하는 최소 수정으로 해결하며 업무 계약을 바꾸지 않는다.

`OrderEventKafkaConfig`의 조립 순서:

```text
props = KafkaProperties.buildProducerProperties()의 새 map
props.remove(ProducerConfig.RETRIES_CONFIG)
  // 공통 producer.retries=3을 실제 유효 map에서 제거. 앱 YAML의 생략만으로 해결하지 않음
props.putAll(commerce.order-events.producer-properties)
  // 아래 표는 이 namespace의 기본값. 공통 consumer 설정에는 적용하지 않음
if 최종 props에 ProducerConfig.TRANSACTIONAL_ID_CONFIG key가 존재:
  기동 설정 오류로 거절 // 값 제거·비트랜잭션으로 조용히 전환하지 않음
props의 serializer/type header/acks/idempotence/timeout 유효 조합 확인
factory = 전용 DefaultKafkaProducerFactory<String, Object>
  // StringSerializer, 기존 ObjectMapper를 사용하는 JsonSerializer를 명시적으로 연결
  // JsonSerializer는 properties 경로로 add.type.headers=false 적용, setter/configure 혼용 금지
factory.setPhysicalCloseTimeout(physicalCloseTimeoutSeconds)
if factory.transactionCapable():
  기동 설정 오류로 거절 // 아직 실제 producer 생성·send로 시험하지 않음
template = 이 factory만 사용하는 KafkaTemplate<String, Object>
transactionIdPrefix·producerPerThread·autoFlush는 사용하지 않음
```

명시적인 prefix setter 호출 여부만으로 비트랜잭션이라고 단정하지 않는다. 최종 config map과 factory의 실제 capability를 모두 확인한다. 설정 오류 메시지는 충돌 key와 고정된 설명으로 남기고 설정값 전체를 출력하지 않는다. serializer는 ObjectMapper 생성자와 properties 구성만 사용하며 `configureSerializers` 기본 동작을 끄지 않는다. type header 설정 setter를 추가해 properties 경로와 혼용하지 않는다.

|설정 key|초기값|이유·한계|
|---|---|---|
|`acks`|`all`|ISR에 따른 broker 확인. 단일 broker의 복제 장애 내구성 입증은 아님|
|`enable.idempotence`|`true`|producer 프로토콜 내부 재시도 보호. 별도 send 중복·소비 DB 중복은 별개|
|`max.in.flight.requests.per.connection`|`5`|idempotence와 호환. 이유 없는 1개 직렬화는 하지 않음|
|`retries`|명시 제거, client 기본값|횟수 3으로 먼저 포기하지 않고 전달 제한 시간 안에서 재시도. Kafka clients 3.8.1 기본값은 2147483647이며 무한 전달 보장이 아님|
|`retry.backoff.ms` / `retry.backoff.max.ms`|`500` / `1000`|반복 실패의 backoff·jitter. 항상 500ms 고정 간격이 아님|
|`max.block.ms`|`5000`|metadata·buffer 확보 대기 기회. 요청 스레드 점유 비용이 남고 직렬화·HTTP 전체 시간 상한은 아님|
|`request.timeout.ms`|`20000`|기존 개별 요청 대기값 유지. 정상 응답 지연을 과하게 실패시키지 않으려는 초기 가설|
|`delivery.timeout.ms`|`30000`|전달·재시도의 시간 예산. 회복 불가 오류·배치 만료 등으로 더 일찍 완료될 수 있음|
|`linger.ms`|`0`|소량 이벤트에 의도적인 batch 대기를 추가하지 않음|
|`key.serializer` / `value.serializer`|String / JSON|5.8 계약. Java 클래스 header에 소비 계약을 의존시키지 않음|
|`spring.json.add.type.headers`|`false`|공통 YAML에도 있는 값을 전용 producer에 실제 적용|

수치는 운영 적정값이나 실측 결과가 아니다. 초안은 정상 최초 전송·일시적 지연에 여유를 주는 쪽을 택했고, cold start·broker 장애에서 요청/결과 관측이 길어지는 비용을 수용한다. `delivery.timeout.ms >= request.timeout.ms + linger.ms`, idempotence의 acks·retries·in-flight 호환을 확인한다. producer 최초 생성·객체 직렬화 등 모든 지연을 5초로 보장하지 않는다. 바인딩된 YAML과 실제 factory map·serializer·전송 metadata를 구분해 검증한다.

### broker와 정상 종료

local·test의 producer·consumer·admin은 각 실행에서 같은 실제 broker의 접근 가능한 bootstrap 주소를 받는다. 호스트 `localhost:19092`와 컨테이너 내부 `kafka:9092`를 같은 접근 경로라고 가정하지 않는다. 테스트는 고유 topic·group과 소유한 자원만 정리한다. topic 생성은 준비 단계의 명시적 admin 작업으로 하며 send의 자동 생성에 기대지 않는다. 단일 broker topic의 복제 계수·min ISR가 `acks=all` 쓰기를 허용하는지 확인한다. 애플리케이션의 운영 partition 수·증설 정책은 이번 발행 초안에서 새로 정하지 않는다. 필수 배정 실험 topic은 원문대로 partition 2개다.

HTTP는 기존 `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`를 명시한다. 전용 singleton·비트랜잭션 producer의 physical close 대기는 15초다. HTTP 진행 요청의 사건 등록·send가 끝난 뒤 factory가 종료되는 실제 lifecycle 순서를 확인한다. Spring Kafka 3.3.4 소스의 factory phase는 `Integer.MIN_VALUE`, stop은 destroy로 연결되지만 실제 bean 의존성과 종료 로그 검증을 대신하지 않는다.

정상 실행의 전달 예산 30초를 종료 때 끝까지 주겠다는 계약이 아니다. close가 시작되면 15초 대기 후 미완료 record가 실패할 수 있으며, 그 결과를 관측 가능한 범위에서 로그로 남긴다. phase 대기는 동기 stop을 강제로 중단하는 전역 deadline이 아니고, phase 30초와 close 15초를 더해 앱 전체 45초 종료 보장으로 쓰지 않는다. 별도 flush·종료 executor·진행 이벤트 영속화는 추가하지 않는다. OS 강제 종료·전원 장애에는 callback 로그도 남지 않을 수 있다.

## 4.6 주문 확정 이벤트 발행

**상태:** 2026-10-28 발행 측 상세설계 초안. 아래 코드는 처리 계약을 설명하는 의사코드이며 구현·실행 결과가 아니다. 4.3의 PG 검증·잠금·예약·충돌 보존을 유지하고 최초 확정 분기에만 사건을 등록한다. 클래스는 2.11, 저장·시각은 3.7, wire 계약·로그는 5.8, 대안·한계는 A.34를 따른다.

### 공통 결과 적용과 snapshot 생성

```text
PaymentTransactionService.applyObservation(token, observation): // 별도 Bean의 public TX B
  첫 업무 SQL = Order PK 잠금, 그 뒤 Payment 처음 일반 조회
  기존 연결·금액·고정 품목·상태 검사 + 3.7의 PG 상태별 confirmedAt 검사
  decision = 기존 payment.recordObservation(token, observation, 기존 확인 시각)
  event = null
  switch decision.effect:
    CONFIRM:
      now = ZonedDateTime.now(orderConfirmationClock)
            .withZoneSameInstant(UTC).truncatedTo(MICROS)
      order.confirmPg(payment.amount, now) // 상태·금액·시각을 함께 기록
      event = OrderConfirmed.from(order)   // TX 안 scalar 복사; Entity 보관 없음
      outcome = FINAL
    RELEASE:
      기존 재고 반환·order.failPg(), outcome = FINAL // 사건 없음
    FINAL_UNCHANGED:
      outcome = FINAL // 시각 갱신·사건 등록·미발행 복구 시도 없음
    PENDING / UNAVAILABLE / CONFLICT:
      기존 분기·충돌 요약대로 처리 // 사건 없음
  기존 Order·Payment 저장
  if event != null:
    ApplicationEventPublisher.publishEvent(event) // 아직 Kafka send가 아님
  기존 BEFORE_COMMIT probe // 주입 실패·flush/commit 오류면 전체 rollback
  return 기존 PaymentApplyResult(OrderInfo, outcome)
  // 실제 commit은 프록시가 수행. 반환 직전이나 publishEvent 위치를 commit 뒤로 해석하지 않음

OrderModel.confirmPg(amount, confirmedAt):
  PG·PAYMENT_PENDING·저장 confirmedAt 없음·amount=저장 totalAmount 확인
  입력 시각의 null·UTC·마이크로초 정밀도 확인
  검사 실패: INTERNAL_ERROR, 일부 값 먼저 변경하지 않음
  paidAmount=amount, status=CONFIRMED, this.confirmedAt=confirmedAt

OrderConfirmed.from(order):
  3.7의 주문·시각·저장 품목 전제 확인
  각 원본 항목의 productId·quantity를 합산 전에 양수 검사
  수량 map = productId별 Math.addExact 합산; 합산 결과 양수·overflow 검사
  entries를 productId 오름차순 Item record로 변환·불변 List 복사
  return OrderConfirmed(양수 orderId, 저장 confirmedAt, copiedItems)
```

즉시 생성 SUCCESS·권위 조회 callback·reconcile은 기존 공통 applier 호출을 유지한다. OrderConfirmFacade의 저장 결과 반환·대기·차단 경로, 읽기 API, POINT fixture에서는 내부 사건을 등록하지 않는다. 같은 Order 잠금으로 최초 CONFIRM을 직렬화하되 실제 MySQL 경쟁 검증을 수행해야 한다. 사건 생성·등록 오류는 TX 안의 오류이므로 기존 500·rollback이고, commit 실패 뒤 별도 거래 unique 충돌 기록 TX에서도 사건을 만들지 않는다. snapshot이 이미 등록되었어도 원 TX rollback이면 AFTER_COMMIT에서 실행하지 않는다.

### commit 이후 전송과 실패 관측

```text
OrderConfirmedEventListener.on(event): // AFTER_COMMIT, fallbackExecution=false, @Async 없음
  try:
    publicationProbe.beforeSend(event) // no-op 기본 구현. 실패 주입은 여기서만 send 전 차단
    orderEventPublisher.publish(event)  // 한 번 호출; DB 조회·상태 갱신 없음
  catch RuntimeException failure:
    이 절의 안전 로그(eventId, orderId, 단계 LISTENER_CALL, 원인 타입)
    return // 이미 끝난 commit을 취소하거나 예외를 PG 실패로 바꾸지 않음

KafkaOrderEventPublisher.publish(event): // TX 없음, 전용 KafkaTemplate qualifier
  try:
    payload = OrderConfirmedV1(
      "OrderConfirmed", 1, event.eventId(), event.orderId,
      ISO_INSTANT.format(event.confirmedAt.toInstant()), copiedItems)
    key = Long.toString(event.orderId)
    future = template.send(configuredTopic, key, payload) // 명시 partition 없음
  catch RuntimeException failure:
    이 절의 안전 로그(단계 SEND_CALL) // 변환·직렬화·send 호출 오류 포함
    return
  future.whenComplete((result, failure) ->
    if failure != null:
      이 절의 안전 로그(단계 SEND_COMPLETION, 원인 타입)
    else:
      이 절의 성공 로그(result.recordMetadata의 topic, partition, offset)
  )
  return // get/join/flush·whenCompleteAsync·자체 retry 없음
```

기본 Spring 이벤트 multicaster의 동기 등록 흐름을 유지하고 이 사건을 처리하는 일반 `@EventListener`·`BEFORE_COMMIT` Kafka 발행자를 추가하지 않는다. TX 밖의 publishEvent는 `fallbackExecution=false`로 대상 listener를 실행하지 않으며, 실제 생산 경로는 최초 CONFIRM TX 한 곳으로 제한한다. send 자체의 metadata·buffer 대기는 2.11대로 요청 스레드에 남고 Future의 최종 완료는 기다리지 않는다. 이미 완료된 Future의 callback은 등록 시 바로 실행될 수도 있으므로 무조건 HTTP 반환 뒤 로그가 나온다고 약속하지 않는다.

발행 adapter의 동기 오류는 adapter가 기록하고 정상 반환하며 listener는 probe/포트 외곽의 예기치 않은 RuntimeException만 기록해 같은 오류의 중복 로그를 피한다. callback은 불변 scalar·DTO만 캡처하고 짧은 로그 처리만 수행한다. DB·PG·재발행·대기·새 executor는 호출하지 않는다. JVM의 치명적 Error까지 catch하여 정상 성공처럼 숨기지 않는다. 로그는 실패 시점을 관측하는 수단이며 영속 복구 보장이 아니다.

|실패 시점|주문·예약|발행·응답의 의미|
|---|---|---|
|상태 전이·snapshot·사건 등록·DB flush/commit 전 실패|TX B 전체 rollback, 이전 예약/주문 상태 유지|원 TX의 Kafka send 없음. 기존 기술 오류·거래 unique 처리 유지|
|commit 뒤 probe 또는 send 호출 실패|CONFIRMED·confirmedAt·예약 소비 유지|로그로 별도 발행 실패. 기존 확정 응답, 새 PG create·주문 후퇴 없음|
|Future 실패·timeout|저장 확정 유지|broker 미기록이라고 단정하지 않음. 같은 시도의 callback/reconcile도 자동 재발행하지 않음|
|commit 뒤·send 전 프로세스 종료|저장 확정 유지|미발행 가능. 이번 직접 발행의 남은 공백이며 재시작으로 자동 복구되지 않음|
|정상 종료 close 예산 소진|저장 확정 유지|미완료 전송 실패 가능. 30초 전달 예산을 모두 주는 계약 아님|

### 발행 측 검증 기준 — 실행 전

다음은 구현 단위에서 수행할 통과 기준이다. 이 초안 작성 중에는 실행하지 않았다. 기존 PG 전체 테스트 수치·mock 호출 성공을 실제 Kafka 발행 검증으로 대신하지 않는다.

|ID·계층|사례와 필수 관찰|
|---|---|
|P1 domain·snapshot|최초 확정 시각 설정, 재확정 거절, 동일 ID 유도, 원본 각각의 양수 수량 검사 뒤 중복 합산·overflow 검사, 불변 List·품목. 같은 상품의 -1·3이 합계 2로 은폐되지 않고 실패. 외부 Entity 변경 영향 없음. 경계 동작 중심으로 검증|
|P2 실제 Spring TX·MySQL|즉시 SUCCESS·callback·reconcile 각각 최초 확정 commit 뒤 포트 호출. 포트 호출 시점에 기존 스레드의 TX에 참여하지 않는 별도 DB 연결/독립 TX로 CONFIRMED·confirmedAt이 보이는지 확인. listener 호출 여부나 isActualTransactionActive 플래그만으로 commit을 입증하지 않음. callback/reconcile 동시 경쟁은 실제 Order 잠금으로 등록 1회·시각 불변. 일반 포트 대역은 broker 저장 근거로 쓰지 않음|
|P3 미발행·rollback|DRAFT·PENDING·UNKNOWN·RELEASE·CONFLICT·FINAL_UNCHANGED·POINT 경로 등록 0. 내부 사건 등록 뒤 BEFORE_COMMIT 실패와 실제 commit 제약 실패에서 send 0·업무 전체 rollback. 테스트 상위 TX의 자동 rollback을 commit 성공으로 해석하지 않음|
|P4 저장·직렬화|실제 DDL의 DATETIME(6)·nullable, 고정 Clock의 나노초 입력을 마이크로초 절삭. commit 뒤 1차 캐시가 아닌 새 컨텍스트/직접 SQL로 DB의 Instant·마이크로초 정밀도 비교. 실제 record의 key, 필수 JSON 필드 전체의 이름·타입·값, 시각 Z 표현·수량·type header 미발행 확인. Java 객체의 값만으로 wire 일치를 판단하지 않음|
|P5 전용 설정·오류|factory 실제 map에서 공통 retries=3 미상속·나머지 2.11 값 확인. transactional.id 혼입 기동 거절과 정상 factory.transactionCapable=false, 기존 공통/전용 bean 주입 관계·전용 qualifier·serializer properties 실제 적용을 확인. send 호출 오류와 Future 실패 각각 확정 유지·안전 로그·PG 추가 생성 0. timeout을 미기록 증거로 삼지 않음|
|P6 실제 정상 발행|실제 주문 API·PG fixture·결과 TX·broker를 거쳐 key·eventId·DB 품목·시각·성공 metadata를 새 조회로 비교. producer가 partition을 고르고 실제 위치를 관측. 소비 DB 완료 증거는 후속 소비 설계·검증과 결합|
|P7 commit→send 공백|대상 주문만 beforeSend probe에서 실패 주입. 새 TX/SQL로 CONFIRMED·시각·예약 소비 유지, adapter send 0 확인. 격리 topic의 모든 partition별 시작/끝 offset을 고정하고 각 유한 구간을 끝까지 읽어 eventId record 없음 확인. 주문 commit 뒤 장벽으로 주입 위치를 입증. listener 로그만으로 부재를 주장하지 않으며 모든 Kafka 경로·시점의 미발행으로 일반화하지 않음|
|P8 정상 종료|진행 HTTP 요청의 사건 등록·send와 producer factory close의 순서, 대기 예산·관측 가능한 완료/실패 callback·IO thread 정리를 실제 컨텍스트 종료에서 확인. 정상 완료와 close 예산 소진·관측 누락을 구분한다. callback 로그 없음만으로 전송 성공을 판정하거나 모든 record의 로그 확보를 보장하지 않음. 각 phase timeout을 전체 종료 보장으로 오해하지 않음. 지연 주입 시 통제 실패라고 명시|
|P9 회귀·구조|기존 PG·예약·금액·소유권·POINT 사실·삭제 비노출·캐시와 기존 의미 있는 테스트 유지. 필요한 PG fixture 시각 입력만 새 계약으로 보강하고 이유 기록. 관련 Checkstyle·ArchUnit 포함|

### 다음 주 재현 준비와 검증 범위

실제 PG 결과 적용 경로의 **미발행 1건과 정상 발행·소비 1건**을 재현 코드로 보존한다. 고정 Clock·품목·수량·W6 revision·실행 방법과 각 실행의 실제 `orderId, providerOrderId, transactionKey, eventId, confirmedAt, items`를 기록한다. 주문·예약·broker·활동 결과는 각 경계에서 새 조회로 확인하고 민감정보는 제외한다. W7 재실행에서는 새 사건 ID를 얻어 준비→복구 사이에 고정하며 원래 숫자를 맞춰 원본 행이 보존됐다고 쓰지 않는다. W6 직접 발행 준비 경로를 다시 실행할 수 있게 유지하고 W7 Outbox 결과를 지워 누락을 꾸미지 않는다.

이 과제의 거래 식별자는 기존 로컬 합성 PG/fixture에서 실제 생성한 값이다. 이 재현에 필요한 값을 임의 마스킹하여 원문 필수 근거를 잃지 않으며, 실제 외부 PG 계정·거래나 비밀정보를 사용·공개하는 기능을 추가하지 않는다.

정상 소비 결과·실제 listener 실패/ack·소비 DB commit 뒤 offset 공백·partition 2개와 consumer 1→2→3 배정은 필수로 남는다. 다만 이 단위에서 소비 클래스·오류 복구 의사코드를 작성하지 않고 후속 설계에서 통합한다. P7은 DB→broker 공백, 소비 재전달은 DB→offset 공백이라 서로 대체하지 않는다. 준비·대기에는 deadline을 두고 시작한 consumer·HTTP fixture·broker·DB를 정리하며 임시 DB를 다음 주까지 유지하지 않는다. 선택적 동기/비동기·timeout 비교 실험은 글 주제 선정과 별도 승인 전까지 추가하지 않는다.

### 성공·실패의 공개 의미와 로그

최초 확정 TX commit 뒤 발행 실패는 기존 `PaymentApplyResult`와 HTTP 확정 응답을 바꾸지 않는다. 저장 결과 조회·callback·reconcile은 기존 PG 계약을 따르고 Kafka 복구 명령으로 사용하지 않는다. 신규 HTTP 오류 코드·발행 성공 플래그·확정 시각 응답 필드는 추가하지 않는다. `send`의 반환은 broker 기록도 구매 활동 완료도 아니고, 성공 Future의 metadata는 설정된 acks 조건에서 broker가 전송을 확인한 위치다. 구매 활동 DB commit은 별도 관찰한다.

|로그 사건|필수 필드·의미|
|---|---|
|`ORDER_CONFIRMED_SEND_SUCCESS` (INFO)|eventId, orderId, 실제 topic·partition·offset. broker 전송 확인이며 소비 완료 아님|
|`ORDER_CONFIRMED_SEND_FAILURE` (WARN)|eventId, orderId, configured topic, stage=`LISTENER_CALL` / `SEND_CALL` / `SEND_COMPLETION`, exceptionType와 식별 가능한 causeType. 발행 후속 처리 오류이며 주문 취소 아님|

stage는 발생 구간이며 broker 수신 여부 판정이 아니다. `LISTENER_CALL`은 probe/발행 포트 외곽 오류, `SEND_CALL`은 DTO 생성부터 template.send 반환까지의 오류(네트워크 전송 시작 전 오류 포함), `SEND_COMPLETION`은 Future 완료 결과의 실패를 뜻한다.

실패 로그는 원문 exception message·Throwable stacktrace·DTO.toString·payload·카드 값을 무조건 출력하지 않는다. stage·예외 클래스와 고정된 안전한 설명을 사용한다. 관련 주문을 내부 ID로 찾을 수 있게 하고 오류 타입을 모두 “중복”이나 “확실한 미기록”으로 바꾸지 않는다. 알려진 timeout은 결과 불확실 가능성을 설명하되 전체 예외 분류 체계를 새로 만들지 않는다. Kafka 라이브러리 자체 로그의 안전성도 직렬화 실패 검증에서 확인하며, payload 자체에도 불필요한 개인정보를 넣지 않는다.

로그 관측만 제공하며 별도 발행 상태 테이블·지표·알림·대시보드·재발행 보장은 없다. callback은 로그 외 DB/외부 업무를 수행하지 않는다. 프로세스가 종료되거나 로그 저장소가 실패하면 실패 기록이 없을 수도 있으며, 이를 전송 성공으로 해석하지 않는다. 설정·실제 metadata·새 DB 조회를 함께 사용하는 검증 기준은 4.6이다.
