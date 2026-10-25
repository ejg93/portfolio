package com.portfolio.cheongyak.rules;

import com.portfolio.cheongyak.Fixtures;
import com.portfolio.cheongyak.HouseType;
import com.portfolio.cheongyak.Notice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 판정 규칙. 「실측 회귀」 는 2026-10-25 공고문 30건 정독에서 시안 로직이 틀렸던 사례다(doc/backend/cheongyak-rule-audit.md)
class JudgeTest {

    /** 서울 거주 무주택 세대주, 통장 84개월·84회, 예치금 300만원 */
    private static Applicant.ApplicantBuilder seoul() {
        return Applicant.builder().residenceSido("서울").birthDate(LocalDate.of(1988, 3, 1)).householdHomeless(true)
                .householder(true).accountMonths(84).paymentCount(84).depositManwon(300).residenceMonthsInSido(60);
    }

    private static Applicant.ApplicantBuilder gyeonggi() {
        return seoul().residenceSido("경기").residenceMonthsInSido(10);
    }

    private static Judgement one(Notice n, double area, Applicant a) {
        return Judge.judge(n, List.of(Fixtures.type(n, "01", area).build()), a).get(0);
    }

    private static Judgement one(Notice n, HouseType t, Applicant a) {
        return Judge.judge(n, List.of(t), a).get(0);
    }

    private static Notice ok(Notice.NoticeBuilder b) {
        return b.docStatus("OK").build();
    }

    @Nested
    @DisplayName("민영(제28조)")
    class Private {

        @Test
        @DisplayName("서울 거주 85.00㎡ 예치 300 → 1순위, 85.01㎡ → 2순위(600 필요)")
        void depositBoundary() {
            Notice n = ok(Fixtures.apt("P1"));
            assertThat(one(n, 85.00, seoul().build()).status()).isEqualTo(Judgement.RANK1);
            Judgement over = one(n, 85.01, seoul().build());
            assertThat(over.status()).isEqualTo(Judgement.RANK2);
            assertThat(over.reasons()).anyMatch(r -> r.code().equals("DEPOSIT") && r.text().contains("필요 600만원"));
        }

        @Test
        @DisplayName("규제지역 가입 23개월 → 2순위, 24개월 → 1순위")
        void regulatedAccount() {
            Notice n = ok(Fixtures.apt("P2").specltRdnAt("Y").mdatTrgetAt("Y"));
            assertThat(one(n, 59.9, seoul().accountMonths(23).build()).status()).isEqualTo(Judgement.RANK2);
            assertThat(one(n, 59.9, seoul().accountMonths(24).build()).status()).isEqualTo(Judgement.RANK1);
        }

        @Test
        @DisplayName("규제지역 2주택 세대 → 2순위, 1주택 → 1순위지만 가점제 제외 참고")
        void houses() {
            Notice n = ok(Fixtures.apt("P3").mdatTrgetAt("Y"));
            assertThat(one(n, 59.9, seoul().householdHomeless(false).householdHouseCount(2).build()).status()).isEqualTo(Judgement.RANK2);
            Judgement oneHouse = one(n, 59.9, seoul().householdHomeless(false).householdHouseCount(1).build());
            assertThat(oneHouse.status()).isEqualTo(Judgement.RANK1);
            assertThat(oneHouse.reasons()).anyMatch(r -> r.code().equals("POINT_EXCLUDED") && !r.decisive());
        }

        @Test
        @DisplayName("가점제 비율 — 투기과열 60㎡ 40%·84㎡ 70%·101㎡ 80%, 청약과열 101㎡ 50%")
        void lawPointShare() {
            Notice spec = ok(Fixtures.apt("P4").specltRdnAt("Y").mdatTrgetAt("Y"));
            Notice over = ok(Fixtures.apt("P5").mdatTrgetAt("Y"));
            Applicant rich = seoul().depositManwon(1500).build();
            assertThat(one(spec, 60, rich).lottery().pointShare()).isEqualTo(0.40);
            assertThat(one(spec, 84, rich).lottery().pointShare()).isEqualTo(0.70);
            assertThat(one(spec, 101, rich).lottery().pointShare()).isEqualTo(0.80);
            assertThat(one(over, 101, rich).lottery()).extracting(Judgement.Lottery::pointShare, Judgement.Lottery::pointShareSource)
                    .containsExactly(0.50, "LAW");
        }

        @Test
        @DisplayName("비규제는 공고문 표기 비율(DOC)을 쓰고, 그것도 없으면 NONE — 둘 다 참고라 확인 필요는 안 켠다")
        void docPointShare() {
            Judgement doc = one(ok(Fixtures.apt("P6").docPointLe85(40)), 84, seoul().build());
            assertThat(doc.lottery()).extracting(Judgement.Lottery::pointShare, Judgement.Lottery::pointShareSource).containsExactly(0.40, "DOC");
            Judgement none = one(ok(Fixtures.apt("P7")), 84, seoul().build());
            assertThat(none.lottery().pointShareSource()).isEqualTo("NONE");
            assertThat(none.hasCheck()).isFalse();
        }

        @Test
        @DisplayName("특별공급만 있는 비규제 공고는 확인 필요가 안 켜진다(특공은 참고)")
        void specialSupplyIsReference() {
            Notice n = ok(Fixtures.apt("P8"));
            Judgement j = one(n, Fixtures.type(n, "01", 84).spsplyHshldco(40).build(), seoul().build());
            assertThat(j.hasCheck()).isFalse();
            assertThat(j.reasons()).anyMatch(r -> r.code().equals("SPECIAL") && !r.decisive());
        }
    }

    @Nested
    @DisplayName("국민(제27조)")
    class National {

        @Test
        @DisplayName("유주택 → 신청 불가")
        void ownsHouse() {
            Notice n = ok(Fixtures.apt("N1").houseDtlSecd("03"));
            assertThat(one(n, 59, seoul().householdHomeless(false).householdHouseCount(1).build()).status()).isEqualTo(Judgement.INELIGIBLE);
        }

        @Test
        @DisplayName("수도권 12회 → 1순위, 11회 → 2순위. 소득·자산 기준 때문에 늘 확인 필요")
        void payments() {
            Notice n = ok(Fixtures.apt("N2").houseDtlSecd("03"));
            Judgement twelve = one(n, 59, seoul().paymentCount(12).build());
            assertThat(twelve.status()).isEqualTo(Judgement.RANK1);
            assertThat(twelve.hasCheck()).isTrue();
            assertThat(twelve.reasons()).anyMatch(r -> r.code().equals("EXTEND") && !r.decisive());
            assertThat(one(n, 59, seoul().paymentCount(11).build()).status()).isEqualTo(Judgement.RANK2);
        }
    }

    @Nested
    @DisplayName("판정 대상 아님")
    class NotJudged {

        @Test
        @DisplayName("오피스텔·신혼희망타운·분양전환 임대 → NOT_APPLICABLE")
        void notApplicable() {
            assertThat(Judge.judge(Fixtures.apt("O1").kind("URBTY").houseSecd("02").build(), List.of(), seoul().build()).get(0).status())
                    .isEqualTo(Judgement.NOT_APPLICABLE);
            assertThat(Judge.judge(Fixtures.apt("O2").houseSecd("10").build(), List.of(), seoul().build()).get(0).status())
                    .isEqualTo(Judgement.NOT_APPLICABLE);
            assertThat(Judge.judge(Fixtures.apt("O3").rentSecd("1").build(), List.of(), seoul().build()).get(0).status())
                    .isEqualTo(Judgement.NOT_APPLICABLE);
        }
    }

    @Nested
    @DisplayName("실측 회귀 — 무순위 거주지(제26조⑤ + 공고문)")
    class Remndr {

        private Judgement judge(String area, Applicant a) {
            return Judge.judge(ok(Fixtures.remndr("R").docRemndrArea(area).docRemndrHomeless("Y")), List.of(), a).get(0);
        }

        @Test
        @DisplayName("경기 거주자만인 공고에 서울 거주 → 신청 불가")
        void gyeonggiOnly() {
            assertThat(judge("경기", seoul().build()).status()).isEqualTo(Judgement.INELIGIBLE);
        }

        @Test
        @DisplayName("서울 거주자만인 공고에 경기 거주 → 신청 불가")
        void seoulOnly() {
            assertThat(judge("서울", gyeonggi().build()).status()).isEqualTo(Judgement.INELIGIBLE);
        }

        @Test
        @DisplayName("수도권 공고에 인천 거주, 전국 공고 → 신청 가능")
        void capitalAndNation() {
            assertThat(judge("수도권", seoul().residenceSido("인천").build()).status()).isEqualTo(Judgement.RANK1);
            assertThat(judge("전국", seoul().residenceSido("부산").build()).status()).isEqualTo(Judgement.RANK1);
        }

        @Test
        @DisplayName("유주택 → 신청 불가")
        void ownsHouse() {
            assertThat(judge("전국", seoul().householdHomeless(false).householdHouseCount(1).build()).status())
                    .isEqualTo(Judgement.INELIGIBLE);
        }

        @Test
        @DisplayName("공고문을 못 받았으면 신청 가능 + 확인 필요")
        void docMissing() {
            Judgement j = Judge.judge(Fixtures.remndr("R").docStatus("NO_LINK").build(), List.of(), seoul().build()).get(0);
            assertThat(j.status()).isEqualTo(Judgement.RANK1);
            assertThat(j.hasCheck()).isTrue();
            assertThat(j.receipt().label()).isEqualTo("무순위");
        }
    }

    @Nested
    @DisplayName("실측 회귀 — 1순위 접수 구분")
    class Receipt {

        private final Notice.NoticeBuilder gyeonggiNotice = Fixtures.apt("G").areaNm("경기").docStatus("OK")
                .rnk1GgBgnde(LocalDate.of(2026, 10, 10)).rnk1GgEndde(LocalDate.of(2026, 10, 10));

        @Test
        @DisplayName("서울 공고에 서울 거주 → 해당지역, 경기 공고(경기 접수일 있음)에 경기 거주 → 경기는 시도 해당지역이 먼저")
        void basic() {
            assertThat(one(ok(Fixtures.apt("S")), 59, seoul().build()).receipt().label()).isEqualTo("해당지역");
            assertThat(one(gyeonggiNotice.build(), 59, gyeonggi().build()).receipt().label()).isEqualTo("해당지역");
        }

        @Test
        @DisplayName("인천 공고(경기 접수일 없음)에 경기 거주 → 기타지역(경기 아님)")
        void incheonNoticeGyeonggiResident() {
            Notice n = ok(Fixtures.apt("I").areaNm("인천"));
            assertThat(one(n, 59, gyeonggi().build()).receipt().label()).isEqualTo("기타지역");
        }

        @Test
        @DisplayName("평택시 1년 해당지역 — 평택 14개월 → 해당지역(평택 1년↑), 6개월 → 경기, 경기 접수일 없으면 기타지역")
        void cityLevel() {
            Notice n = gyeonggiNotice.docCrspCity("평택").docCrspMonths(12).build();
            assertThat(one(n, 84, gyeonggi().residenceCity("평택").residenceMonthsInCity(14).build()).receipt().label())
                    .isEqualTo("해당지역(평택 1년↑)");
            assertThat(one(n, 84, gyeonggi().residenceCity("평택").residenceMonthsInCity(6).build()).receipt().label())
                    .isEqualTo("경기");
            Notice noGg = n.toBuilder().rnk1GgBgnde(null).rnk1GgEndde(null).build();
            assertThat(one(noGg, 84, gyeonggi().residenceCity("평택").residenceMonthsInCity(6).build()).receipt().label())
                    .isEqualTo("기타지역");
        }

        @Test
        @DisplayName("시 단위 해당지역인데 경기 거주자가 시군을 안 넣으면 확인 필요")
        void cityMissing() {
            Notice n = gyeonggiNotice.docCrspCity("평택").docCrspMonths(12).build();
            Judgement j = one(n, 84, gyeonggi().build());
            assertThat(j.hasCheck()).isTrue();
            assertThat(j.reasons()).anyMatch(r -> r.code().equals("CITY_INPUT"));
        }

        @Test
        @DisplayName("서울 2년 거주 우선 공고 — 서울 10개월은 기타지역, 30개월은 해당지역")
        void residencePriority() {
            Notice n = ok(Fixtures.apt("SP").docResPriArea("서울").docResPriMonths(24));
            assertThat(one(n, 59, seoul().residenceMonthsInSido(10).build()).receipt().label()).isEqualTo("기타지역");
            assertThat(one(n, 59, seoul().residenceMonthsInSido(30).build()).receipt().label()).isEqualTo("해당지역");
        }

        @Test
        @DisplayName("공고문을 못 받은 APT 는 확인 필요")
        void docMissing() {
            assertThat(one(Fixtures.apt("D").docStatus("NO_LINK").build(), 59, seoul().build()).hasCheck()).isTrue();
        }
    }
}
