package com.portfolio.cheongyak;

import lombok.Builder;

import java.math.BigDecimal;

/** 공고의 주택형 한 줄. V1 의 cheongyak_house_type 컬럼과 1:1 */
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
        String rawJson
) {

    public String noticeKey() {
        return houseManageNo + ":" + pblancNo;
    }
}
