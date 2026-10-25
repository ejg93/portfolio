package com.portfolio.cheongyak.rules;

import java.time.LocalDate;
import java.util.List;

/**
 * 공고(APT 는 주택형) 하나에 대한 판정. status 는 RANK1·RANK2·INELIGIBLE·NOT_APPLICABLE — 무순위의 RANK1 은 「신청 가능」.
 * hasCheck 는 decisive 인 CHECK 가 있을 때만 켜진다.
 */
public record Judgement(
        String houseManageNo,
        String pblancNo,
        String modelNo,
        String status,
        boolean hasCheck,
        List<Reason> reasons,
        Score score,
        Lottery lottery,
        Receipt receipt
) {

    public static final String RANK1 = "RANK1";
    public static final String RANK2 = "RANK2";
    public static final String INELIGIBLE = "INELIGIBLE";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";

    /** 별표1 가점. homelessYears 는 공고일 기준 무주택 만 연수 */
    public record Score(int total, int homeless, int dependents, int account, int spouseBonus, int homelessYears) {
    }

    /** 가점제 비율. source 는 LAW(법정값)·DOC(공고문 표기)·NONE(사업주체 공고) */
    public record Lottery(Double pointShare, String pointShareSource, String note) {
    }

    /** 1순위(무순위는 접수) 접수 구분과 날짜 */
    public record Receipt(String label, LocalDate rank1Begin, LocalDate rank1End) {
    }
}
