package com.portfolio.cheongyak;

/** 가점 참고치 원자료 한 줄. 공고 위치(시군구·시도)와 그 주택형의 해당지역 최저 당첨가점 */
public record ScoreSample(String lawdCd, String areaNm, Integer minScore) {
}
