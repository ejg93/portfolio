package com.portfolio.cheongyak.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 별표1 경계값. 테스트 이름이 곧 점수표다
class ScoreCalculatorTest {

    private static final LocalDate NOTICE = LocalDate.of(2026, 10, 1);

    /** 1980-10-01 생 → 공고일에 만 46세. 기본은 무주택·미혼·부양 0·통장 0개월 */
    private static Applicant.ApplicantBuilder base() {
        return Applicant.builder().residenceSido("서울").birthDate(LocalDate.of(1980, 10, 1)).householdHomeless(true);
    }

    @ParameterizedTest(name = "무주택 기산 {0} → {1}점")
    @CsvSource({
        "2014-10-02, 24",   // 11년 364일 — 만 11년
        "2014-10-01, 26",   // 만 12년
        "2011-10-01, 32",   // 만 15년 — 상한
        "2026-09-01, 2"     // 1년 미만
    })
    @DisplayName("무주택기간 — 1년 미만 2, 1년마다 2점, 15년 이상 32")
    void homelessPoints(LocalDate since, int points) {
        assertThat(ScoreCalculator.score(base().homelessSince(since).build(), NOTICE).homeless()).isEqualTo(points);
    }

    @Test
    @DisplayName("만 30세 전 미혼은 무주택 0점")
    void under30Unmarried() {
        assertThat(ScoreCalculator.score(base().birthDate(LocalDate.of(1998, 1, 1)).build(), NOTICE).homeless()).isZero();
    }

    @Test
    @DisplayName("30세 전에 혼인했으면 혼인신고일부터 센다 — 28세 혼인, 공고일 31세 → 만 3년 8점")
    void marriedBefore30() {
        Applicant a = base().birthDate(LocalDate.of(1995, 10, 1)).marriageDate(LocalDate.of(2023, 10, 1)).build();
        assertThat(ScoreCalculator.score(a, NOTICE)).extracting(Judgement.Score::homelessYears, Judgement.Score::homeless)
                .containsExactly(3, 8);
    }

    @Test
    @DisplayName("유주택이면 무주택 0점")
    void ownsHouse() {
        assertThat(ScoreCalculator.score(base().householdHomeless(false).build(), NOTICE).homeless()).isZero();
    }

    @ParameterizedTest(name = "부양가족 {0}명 → {1}점")
    @CsvSource({"0, 5", "6, 35", "9, 35"})
    @DisplayName("부양가족 — 0명 5, 1명마다 5점, 6명 이상 35")
    void dependents(int n, int points) {
        assertThat(ScoreCalculator.score(base().dependents(n).build(), NOTICE).dependents()).isEqualTo(points);
    }

    @ParameterizedTest(name = "통장 {0}개월 → {1}점")
    @CsvSource({"5, 1", "6, 2", "11, 2", "12, 3", "179, 16", "180, 17"})
    @DisplayName("통장 가입기간 — 6개월 미만 1, 1년 미만 2, 이후 1년마다 1점, 15년 이상 17")
    void account(int months, int points) {
        assertThat(ScoreCalculator.score(base().accountMonths(months).build(), NOTICE).account()).isEqualTo(points);
    }

    /*
     * 별표1 비고2: 「그 배우자의 주택청약종합저축 가입기간의 50퍼센트에 해당하는 기간에 대해 이 별표 제2호나목에 따라
     * 산정한 점수(3점을 초과하는 경우에는 3점을 말한다)를 … 합산할 수 있다. 이 경우 합산한 점수가 17점을 초과하는
     * 경우에는 17점으로 한다」 — 기간을 반으로 줄인 뒤 점수표를 쓴다(점수의 50% 가 아니다)
     */
    @Test
    @DisplayName("배우자 통장 10년 → 5년치 점수 7 이지만 3점 상한, 본인 15년(17)과 합쳐도 17 상한")
    void spouseBonus() {
        assertThat(ScoreCalculator.score(base().accountMonths(60).spouseAccountMonths(120).build(), NOTICE))
                .extracting(Judgement.Score::spouseBonus, Judgement.Score::account).containsExactly(3, 10);
        assertThat(ScoreCalculator.score(base().accountMonths(180).spouseAccountMonths(120).build(), NOTICE).account()).isEqualTo(17);
    }
}
