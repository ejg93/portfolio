package com.portfolio.cheongyak.rules;

/**
 * 판정 근거 한 줄. level 은 PASS·FAIL·CHECK·INFO, scope 는 FAIL 이 status 를 어디까지 내리는지(ELIGIBILITY → 신청 불가,
 * RANK1 → 2순위). decisive 는 판정을 바꿀 수 있는 줄 — 화면은 decisive 를 「판정 근거」, 나머지를 「참고」 로 나눈다.
 */
public record Reason(String level, String scope, boolean decisive, String code, String text, String basis) {

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String CHECK = "CHECK";
    public static final String INFO = "INFO";
    public static final String ELIGIBILITY = "ELIGIBILITY";
    public static final String RANK1 = "RANK1";
    public static final String NONE = "NONE";

    static Reason pass(String scope, String code, String text, String basis) {
        return new Reason(PASS, scope, true, code, text, basis);
    }

    static Reason fail(String scope, String code, String text, String basis) {
        return new Reason(FAIL, scope, true, code, text, basis);
    }

    /** 공고문을 봐야 판정이 갈리는 항목. 「확인 필요」 점을 켠다 */
    static Reason check(String code, String text, String basis) {
        return new Reason(CHECK, NONE, true, code, text, basis);
    }

    /** 참고. 판정을 바꾸지 않는다 */
    static Reason info(String code, String text, String basis) {
        return new Reason(INFO, NONE, false, code, text, basis);
    }
}
