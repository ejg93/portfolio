package com.portfolio.cheongyak.rules;

import com.portfolio.cheongyak.rules.Judgement.Score;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 별표1 청약 가점. 기준일은 공고일이라 같은 사람도 공고마다 점수가 다를 수 있다 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static Score score(Applicant a, LocalDate noticeDate) {
        int years = homelessYears(a, noticeDate);
        int homeless = years < 0 ? 0 : RuleTables.homelessPoints(years);
        int dependents = RuleTables.dependentPoints(a.dependents());
        int spouse = a.spouseAccountMonths() > 0 ? Math.min(3, RuleTables.accountPoints(a.spouseAccountMonths() / 2)) : 0;
        int account = Math.min(17, RuleTables.accountPoints(a.accountMonths()) + spouse);
        return new Score(homeless + dependents + account, homeless, dependents, account, spouse, Math.max(0, years));
    }

    /**
     * 무주택 만 연수. 점수가 없는 경우(유주택, 30세 전 미혼)는 -1.
     * 기산일은 만 30세(그 전에 혼인했으면 혼인신고일)이고, 집을 판 적 있으면 최근 무주택이 된 날이 더 늦을 때 그날이다.
     */
    static int homelessYears(Applicant a, LocalDate ref) {
        if (!a.householdHomeless() || a.birthDate() == null) {
            return -1;
        }
        LocalDate age30 = a.birthDate().plusYears(30);
        LocalDate marriage = a.marriageDate() != null && !a.marriageDate().isAfter(ref) ? a.marriageDate() : null;
        if (ref.isBefore(age30) && marriage == null) {
            return -1;
        }
        LocalDate start = marriage != null && marriage.isBefore(age30) ? marriage : age30;
        if (a.homelessSince() != null && a.homelessSince().isAfter(start)) {
            start = a.homelessSince();
        }
        return (int) Math.max(0, ChronoUnit.YEARS.between(start, ref));
    }
}
