package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 주문 확정 이벤트 발행에 쓰는 bean 을 조립한다. */
@Configuration
public class OrderEventKafkaConfig {

    public static final String ORDER_CONFIRMATION_CLOCK = "orderConfirmationClock";

    /** 최초 PG 확정 시각 전용 Clock. 테스트는 이 bean 만 고정 Clock 으로 바꾸며, 다른 생성·감사 시각은 이 Clock 을 쓰지 않는다. */
    @Bean(ORDER_CONFIRMATION_CLOCK)
    Clock orderConfirmationClock() {
        return Clock.systemUTC();
    }
}
