package com.portfolio.cheongyak;

import java.math.BigDecimal;

/** 시세를 계산할 주택형 하나와 그 공고의 위치 */
public record MarketTarget(String houseManageNo, String pblancNo, String modelNo, BigDecimal exclusiveArea,
                           String lawdCd, String umdNm) {
}
