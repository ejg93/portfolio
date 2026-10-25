package com.portfolio.cheongyak;

import java.math.BigDecimal;

/**
 * 공고 주소를 지오코딩한 결과. lawdCd 는 법정동코드(bcode) 앞 5자리이고 실거래 API 의 LAWD_CD 로 그대로 쓴다.
 * umdNm 은 법정동 이름(예 반송동)이다 — 같은 동 실거래를 고를 때 쓴다.
 */
public record Geo(String lawdCd, String bcode, String umdNm, BigDecimal lat, BigDecimal lng, String address) {
}
