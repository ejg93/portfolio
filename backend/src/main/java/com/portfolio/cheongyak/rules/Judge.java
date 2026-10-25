package com.portfolio.cheongyak.rules;

import com.portfolio.cheongyak.HouseType;
import com.portfolio.cheongyak.Notice;
import com.portfolio.cheongyak.NoticeDoc;
import com.portfolio.cheongyak.rules.Judgement.Lottery;
import com.portfolio.cheongyak.rules.Judgement.Receipt;
import com.portfolio.cheongyak.rules.Judgement.Score;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 일반공급 자격·순위 판정. API 필드와 법령 표로 정하고(A·B), 공고문에서 뽑은 정형 값(C1: 무순위 거주지, 시 단위 해당지역)을 쓴다.
 * 그 밖에 공고문에만 있는 조건은 판정하지 않고 「확인 필요」(CHECK) 또는 참고(INFO)로 띄운다.
 * 판정 대상은 APT 민영(제28조)·국민(제27조)과 무순위(제26조⑤)뿐이다.
 */
public final class Judge {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 신혼희망타운(09·10)·불법행위 재공급(06)은 별도 규칙이라 판정하지 않는다
    private static final Set<String> NOT_JUDGED_SECD = Set.of("06", "09", "10");
    private static final Set<String> NOT_JUDGED_KINDS = Set.of("URBTY", "PBLPVT", "OPT");

    private Judge() {
    }

    /** APT 는 주택형마다 하나, 그 밖은 공고에 하나 */
    public static List<Judgement> judge(Notice n, List<HouseType> types, Applicant a) {
        LocalDate ref = n.rcritPblancDe() != null ? n.rcritPblancDe() : LocalDate.now(KST);
        if (NOT_JUDGED_KINDS.contains(n.kind()) || NOT_JUDGED_SECD.contains(n.houseSecd()) || "1".equals(n.rentSecd())) {
            List<Reason> r = List.of(Reason.info("NOT_JUDGED", "주택공급규칙 일반공급 판정 대상 아님 — 공고문으로 확인", "제1조"));
            return List.of(new Judgement(n.houseManageNo(), n.pblancNo(), null, Judgement.NOT_APPLICABLE, false, r,
                    null, null, new Receipt("접수", first(n.rceptBgnde(), n.rnk1CrspBgnde()), first(n.rceptEndde(), n.rnk1CrspEndde()))));
        }
        if ("REMNDR".equals(n.kind())) {
            return List.of(remndr(n, a));
        }
        boolean docOk = NoticeDoc.OK.equals(n.docStatus());
        Receipt receipt = receipt(n, a);
        List<Judgement> out = new ArrayList<>();
        List<HouseType> rows = types.isEmpty() ? Collections.singletonList(null) : types;
        for (HouseType t : rows) {
            List<Reason> r = new ArrayList<>();
            Double area = t == null || t.exclusiveArea() == null ? null : t.exclusiveArea().doubleValue();
            Lottery lottery = null;
            if ("03".equals(n.houseDtlSecd())) {
                national(n, a, area, r);
            } else {
                lottery = privateSector(n, a, area, r);
            }
            common(n, a, t, docOk, r);
            Score score = ScoreCalculator.score(a, ref);
            out.add(new Judgement(n.houseManageNo(), n.pblancNo(), t == null ? null : t.modelNo(), status(r),
                    hasCheck(r), r, score, lottery, receipt));
        }
        return out;
    }

    /** 제26조⑤ — 성년 무주택세대구성원 + 공고문이 정한 거주지역. 통장은 필요 없다 */
    private static Judgement remndr(Notice n, Applicant a) {
        List<Reason> r = new ArrayList<>();
        if (a.householdHomeless()) {
            r.add(Reason.pass(Reason.ELIGIBILITY, "HOMELESS", "무주택세대구성원", "제26조⑤"));
        } else {
            r.add(Reason.fail(Reason.ELIGIBILITY, "HOMELESS", "무주택세대구성원이 아님", "제26조⑤"));
        }
        String area = n.docRemndrArea();
        if (!NoticeDoc.OK.equals(n.docStatus()) || area == null) {
            r.add(Reason.check("DOC_MISSING", "공고문을 못 읽음 — 거주지 자격 직접 확인", "공고문"));
        } else {
            String sido = a.residenceSido();
            boolean ok = "전국".equals(area) || area.equals(sido) || ("수도권".equals(area) && RuleTables.CAPITAL.contains(sido));
            if (ok) {
                r.add(Reason.pass(Reason.ELIGIBILITY, "REMNDR_AREA", "거주지 자격 " + area + " — " + sido + " 거주", "공고문"));
            } else {
                r.add(Reason.fail(Reason.ELIGIBILITY, "REMNDR_AREA", "거주지 자격 " + area + " 거주자만 — " + sido + " 거주라 불가", "공고문"));
            }
        }
        r.add(Reason.info("NO_ACCOUNT", "청약통장 불필요", "제26조⑤"));
        return new Judgement(n.houseManageNo(), n.pblancNo(), null, status(r), hasCheck(r), r, null, null,
                new Receipt("무순위", n.rceptBgnde(), n.rceptEndde()));
    }

    /** 제27조 국민주택 */
    private static void national(Notice n, Applicant a, Double area, List<Reason> r) {
        boolean regulated = regulated(n);
        boolean inCapital = RuleTables.CAPITAL.contains(n.areaNm());
        if (a.householdHomeless()) {
            r.add(Reason.pass(Reason.ELIGIBILITY, "HOMELESS", "무주택세대구성원", "제4조①1"));
        } else {
            r.add(Reason.fail(Reason.ELIGIBILITY, "HOMELESS", "무주택세대구성원이 아님 — 국민주택 신청 불가", "제4조①1"));
        }
        int need = regulated ? 24 : inCapital ? 12 : 6;
        accountMonths(a, need, "제27조①", r);
        if (a.paymentCount() >= need) {
            r.add(Reason.pass(Reason.RANK1, "PAYMENTS", "납입 " + a.paymentCount() + "회 ≥ " + need + "회", "제27조①"));
        } else {
            r.add(Reason.fail(Reason.RANK1, "PAYMENTS", "납입 " + a.paymentCount() + "회 < " + need + "회", "제27조①"));
        }
        if (regulated) {
            householderAndWins(a, "제27조①1다", r);
        }
        r.add(Reason.check("INCOME", "일반공급 소득·자산 기준 — 공고 확인", "공고문"));
        r.add(Reason.info("SEQUENCE", area != null && area <= 40
                ? "순차: 3년 이상 무주택 중 납입횟수 많은 순" : "순차: 3년 이상 무주택 중 저축총액 많은 순", "제27조②"));
        if (inCapital && !regulated) {
            r.add(Reason.info("EXTEND", "시도지사가 24개월·24회로 연장했는지 공고 확인", "제27조①1가"));
        }
    }

    /** 제28조 민영주택. 가점제 비율을 돌려준다 */
    private static Lottery privateSector(Notice n, Applicant a, Double area, List<Reason> r) {
        boolean regulated = regulated(n);
        boolean inCapital = RuleTables.CAPITAL.contains(n.areaNm());
        boolean pubHousing = "Y".equals(n.publicHouseAt()) || "Y".equals(n.nplnPubHouseAt());
        int need = regulated ? 24 : inCapital ? 12 : 6;
        accountMonths(a, need, "제28조①", r);
        if (area == null) {
            r.add(Reason.check("NO_TYPE", "주택형 정보가 없어 예치금을 못 정함 — 공고 확인", "별표2"));
        } else {
            int required = RuleTables.requiredDeposit(a.residenceSido(), area);
            String where = "(" + a.residenceSido() + " 거주·전용 " + area + "㎡)";
            if (a.depositManwon() >= required) {
                r.add(Reason.pass(Reason.RANK1, "DEPOSIT", "예치금 " + a.depositManwon() + "만원 ≥ " + required + "만원 " + where, "별표2"));
            } else {
                r.add(Reason.fail(Reason.RANK1, "DEPOSIT", "예치금 " + (required - a.depositManwon()) + "만원 부족(필요 "
                        + required + "만원) " + where, "별표2"));
            }
        }
        if (regulated) {
            householderAndWins(a, "제28조①1다", r);
            if (a.householdHouseCount() >= 2) {
                r.add(Reason.fail(Reason.RANK1, "HOUSES", "2주택 이상 세대", "제28조①1다"));
            }
        } else if (pubHousing && inCapital && a.householdHouseCount() >= 2) {
            r.add(Reason.fail(Reason.RANK1, "HOUSES", "2주택 이상 세대 — 수도권 공공주택지구", "제28조①1가"));
            r.add(Reason.info("GB", "개발제한구역 해제 50% 이상 지구인지는 공고 확인", "제28조①1가"));
        }
        if (a.householdHouseCount() >= 1 || a.wonByPointsWithin2y()) {
            r.add(Reason.info("POINT_EXCLUDED", "가점제 제외 — 추첨만 가능", "제28조⑥"));
        }
        if (inCapital && regulated) {
            r.add(Reason.info("LOTTERY_HOMELESS", "추첨 물량 75% 는 무주택세대구성원 우선", "제28조⑧"));
        }
        if (area == null) {
            return new Lottery(null, "NONE", null);
        }
        Double law = RuleTables.pointShare(area, regulated || (pubHousing && inCapital),
                "Y".equals(n.specltRdnAt()), "Y".equals(n.mdatTrgetAt()));
        if (law != null) {
            r.add(Reason.info("POINT_SHARE", "가점제 " + Math.round(law * 100) + "% · 추첨 " + (100 - Math.round(law * 100)) + "%", "제28조②④"));
            return new Lottery(law, "LAW", null);
        }
        Integer doc = area <= 60 ? n.docPointLe60() : area <= 85 ? n.docPointLe85() : n.docPointGt85();
        if (doc != null) {
            r.add(Reason.info("POINT_SHARE", "가점제 " + doc + "% (공고문 표기)", "공고문"));
            return new Lottery(doc / 100.0, "DOC", "공고문 표기");
        }
        r.add(Reason.info("POINT_SHARE", "가점제 비율은 사업주체 공고 — 공고 확인", "제28조②"));
        return new Lottery(null, "NONE", null);
    }

    /** APT 공통 — 공고문 못 읽음, 시 단위 해당지역 시군 미입력, 거주 우선·특공·조정대상 참고 */
    private static void common(Notice n, Applicant a, HouseType t, boolean docOk, List<Reason> r) {
        if (!docOk) {
            r.add(Reason.check("DOC_MISSING", "공고문을 못 읽음 — 해당지역 범위·거주 우선 직접 확인", "공고문"));
        }
        if (n.docCrspCity() != null && "경기".equals(a.residenceSido()) && "경기".equals(n.areaNm()) && a.residenceCity() == null) {
            r.add(Reason.check("CITY_INPUT", "해당지역이 시 단위(" + n.docCrspCity() + ") — 거주 시군을 넣으면 접수 구분이 정해진다", "공고문"));
        }
        if (n.docResPriMonths() != null) {
            r.add(Reason.info("RES_PRIORITY", n.docResPriArea() + " " + (n.docResPriMonths() / 12) + "년 이상 거주자 우선 — 현재 "
                    + a.residenceMonthsInSido() + "개월", "공고문"));
        } else if ("Y".equals(n.specltRdnAt())) {
            r.add(Reason.info("RES_PRIORITY", "수도권 투기과열지구는 2년 이상 거주 우선 — 현재 " + a.residenceMonthsInSido() + "개월", "제4조⑤"));
        }
        if (t != null && t.spsplyHshldco() != null && t.spsplyHshldco() > 0) {
            r.add(Reason.info("SPECIAL", "특별공급 " + t.spsplyHshldco() + "세대 — 자격은 공고 확인", "제35~47조"));
        }
        if ("Y".equals(n.mdatTrgetAt())) {
            r.add(Reason.info("MDAT", "조정대상지역은 청약과열로 판정 — 위축지역이면 공고 확인", "제27·28조"));
        }
    }

    /**
     * 1순위 접수 구분. ① 시 단위 해당지역 ② 경기 별도 접수(경기 접수일 칸이 있을 때만) ③ 시도 해당지역(거주 우선 기간 반영) ④ 기타지역
     */
    static Receipt receipt(Notice n, Applicant a) {
        String sido = a.residenceSido();
        boolean sameSido = sido != null && sido.equals(n.areaNm());
        boolean ggSlot = "경기".equals(sido) && "경기".equals(n.areaNm()) && n.rnk1GgBgnde() != null;
        if (n.docCrspCity() != null) {
            int months = n.docCrspMonths() == null ? 0 : n.docCrspMonths();
            if (sameSido && n.docCrspCity().equals(a.residenceCity()) && a.residenceMonthsInCity() >= months) {
                String label = "해당지역(" + n.docCrspCity() + (months > 0 ? " " + months / 12 + "년↑" : "") + ")";
                return new Receipt(label, n.rnk1CrspBgnde(), n.rnk1CrspEndde());
            }
            if (ggSlot) {
                return new Receipt("경기", n.rnk1GgBgnde(), n.rnk1GgEndde());
            }
            return etc(n);
        }
        if (sameSido && (n.docResPriMonths() == null || a.residenceMonthsInSido() >= n.docResPriMonths())) {
            return new Receipt("해당지역", n.rnk1CrspBgnde(), n.rnk1CrspEndde());
        }
        if (ggSlot) {
            return new Receipt("경기", n.rnk1GgBgnde(), n.rnk1GgEndde());
        }
        return etc(n);
    }

    private static Receipt etc(Notice n) {
        return n.rnk1EtcBgnde() != null
                ? new Receipt("기타지역", n.rnk1EtcBgnde(), n.rnk1EtcEndde())
                : new Receipt("기타지역", n.rnk1CrspBgnde(), n.rnk1CrspEndde());
    }

    private static void accountMonths(Applicant a, int need, String basis, List<Reason> r) {
        if (a.accountMonths() >= need) {
            r.add(Reason.pass(Reason.RANK1, "ACCOUNT_MONTHS", "가입 " + a.accountMonths() + "개월 ≥ " + need + "개월", basis));
        } else {
            r.add(Reason.fail(Reason.RANK1, "ACCOUNT_MONTHS", "가입 " + a.accountMonths() + "개월 < " + need + "개월", basis));
        }
    }

    private static void householderAndWins(Applicant a, String basis, List<Reason> r) {
        if (a.householder()) {
            r.add(Reason.pass(Reason.RANK1, "HOUSEHOLDER", "세대주", basis));
        } else {
            r.add(Reason.fail(Reason.RANK1, "HOUSEHOLDER", "세대주 아님", basis));
        }
        if (a.wonWithin5y()) {
            r.add(Reason.fail(Reason.RANK1, "WON5", "세대 내 5년 내 당첨자", basis));
        }
    }

    private static boolean regulated(Notice n) {
        return "Y".equals(n.specltRdnAt()) || "Y".equals(n.mdatTrgetAt());
    }

    private static String status(List<Reason> r) {
        if (r.stream().anyMatch(x -> Reason.FAIL.equals(x.level()) && Reason.ELIGIBILITY.equals(x.scope()))) {
            return Judgement.INELIGIBLE;
        }
        if (r.stream().anyMatch(x -> Reason.FAIL.equals(x.level()) && Reason.RANK1.equals(x.scope()))) {
            return Judgement.RANK2;
        }
        return Judgement.RANK1;
    }

    private static boolean hasCheck(List<Reason> r) {
        return r.stream().anyMatch(x -> Reason.CHECK.equals(x.level()) && x.decisive());
    }

    private static LocalDate first(LocalDate a, LocalDate b) {
        return a != null ? a : b;
    }
}
