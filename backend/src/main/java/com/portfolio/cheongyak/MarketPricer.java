package com.portfolio.cheongyak;

import java.util.List;

/**
 * 주택형 시세(만원) 고르기. 같은 법정동 신축 거래가 표본 수를 채우면 그 중앙값, 아니면 같은 시군구 중앙값.
 * 둘 다 모자라면 시세 없음 — 화면은 「비교 불가」 로 그린다. 평균이 아니라 중앙값인 건 펜트하우스·저층 급매 한두 건에
 * 덜 흔들리게 하려는 것이다.
 */
public final class MarketPricer {

    /** 이보다 적은 거래로는 시세를 내지 않는다 */
    public static final int MIN_SAMPLE = 3;
    public static final String DONG = "DONG";
    public static final String SGG = "SGG";
    public static final Market NONE = new Market(null, 0, null);

    private MarketPricer() {
    }

    /** 시세 중앙값(만원), 표본 수, 기준(DONG·SGG). 시세가 없으면 median·basis 가 null */
    public record Market(Long median, int count, String basis) {
    }

    /** 짝수 개면 가운데 둘의 평균(내림). 비면 null */
    public static Long median(List<Long> amounts) {
        if (amounts.isEmpty()) {
            return null;
        }
        List<Long> s = amounts.stream().sorted().toList();
        int mid = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(mid) : (s.get(mid - 1) + s.get(mid)) / 2;
    }

    public static Market pick(List<Long> dong, List<Long> sgg) {
        if (dong.size() >= MIN_SAMPLE) {
            return new Market(median(dong), dong.size(), DONG);
        }
        if (sgg.size() >= MIN_SAMPLE) {
            return new Market(median(sgg), sgg.size(), SGG);
        }
        return NONE;
    }
}
