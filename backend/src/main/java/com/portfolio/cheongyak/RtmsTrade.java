package com.portfolio.cheongyak;

import lombok.Builder;

import java.math.BigDecimal;

/** 아파트 매매 실거래 한 건(cheongyak_rtms_trade). 금액은 만원, 해제 신고된 거래는 canceled = Y */
@Builder
public record RtmsTrade(
        String lawdCd,
        String dealYmd,
        String umdNm,
        String aptNm,
        String jibun,
        BigDecimal excluUseAr,
        Long dealAmount,
        Integer floorNo,
        Integer buildYear,
        Integer dealDay,
        String canceled,
        String dealingGbn,
        String rawJson
) {
}
