package com.portfolio.cheongyak;

import lombok.Builder;

import java.math.BigDecimal;

/** APT 당첨가점 한 줄(cheongyak_score). 주택형 × 거주 구분 */
@Builder
public record ScoreRow(
        String houseManageNo,
        String pblancNo,
        String houseTy,
        String resideNm,
        Integer minScore,
        Integer maxScore,
        BigDecimal avgScore,
        String rawJson
) {
}
