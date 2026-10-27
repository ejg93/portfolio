package com.portfolio.cheongyak;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 공고의 주택형 한 줄. cheongyak_house_type 컬럼과 1:1. mkt_* 는 V2 의 시세(만원)이고 수집 끝 단계가 채운다 */
@Builder(toBuilder = true)
public record HouseType(
        String houseManageNo,
        String pblancNo,
        String modelNo,
        String houseTy,
        BigDecimal exclusiveArea,
        BigDecimal supplyArea,
        Integer suplyHshldco,
        Integer spsplyHshldco,
        Integer mnychHshldco,
        Integer nwwdsHshldco,
        Integer lfeFrstHshldco,
        Integer oldParntsHshldco,
        Integer insttHshldco,
        Integer etcHshldco,
        Integer transrHshldco,
        Integer ygmnHshldco,
        Integer nwbbHshldco,
        Long topAmount,
        String rawJson,
        Long mktMedian,
        Integer mktCount,
        String mktBasis,
        LocalDateTime mktAt
) {

    public String noticeKey() {
        return houseManageNo + ":" + pblancNo;
    }
}
