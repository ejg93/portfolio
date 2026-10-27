package com.portfolio.cheongyak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketPricerTest {

    @Test
    @DisplayName("중앙값 — 홀수는 가운데, 짝수는 가운데 둘의 평균(내림), 비면 null")
    void median() {
        assertThat(MarketPricer.median(List.of(90000L, 70000L, 80000L))).isEqualTo(80000L);
        assertThat(MarketPricer.median(List.of(70001L, 70000L))).isEqualTo(70000L);
        assertThat(MarketPricer.median(List.of())).isNull();
    }

    @Test
    @DisplayName("같은 동 3건이면 동 기준, 동 2건·시군구 5건이면 시군구 기준, 둘 다 모자라면 없음")
    void pick() {
        assertThat(MarketPricer.pick(List.of(1L, 2L, 3L), List.of(1L, 2L, 3L, 4L)))
                .extracting(MarketPricer.Market::basis, MarketPricer.Market::count).containsExactly("DONG", 3);
        assertThat(MarketPricer.pick(List.of(1L, 2L), List.of(1L, 2L, 3L, 4L, 5L)))
                .extracting(MarketPricer.Market::basis, MarketPricer.Market::median).containsExactly("SGG", 3L);
        assertThat(MarketPricer.pick(List.of(1L), List.of(1L, 2L))).isEqualTo(MarketPricer.NONE);
    }
}
