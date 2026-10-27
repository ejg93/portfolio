package com.portfolio.cheongyak;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 청약 경쟁률 한 줄(cheongyak_cmpet). 주택형 × 거주 구분 × 순위이고 무순위는 rankCode 0 이다.
 * 경쟁률이 「미달」「-」 처럼 숫자가 아니면 cmpetRate 는 null 이고 원래 표기는 cmpetText 에 남긴다.
 */
@Builder
public record CmpetRow(
        String houseManageNo,
        String pblancNo,
        String houseTy,
        String resideNm,
        int rankCode,
        Integer suplyHshldco,
        Integer reqCnt,
        BigDecimal cmpetRate,
        String cmpetText,
        String rawJson
) {
}
