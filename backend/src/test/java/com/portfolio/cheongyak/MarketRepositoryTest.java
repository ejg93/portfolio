package com.portfolio.cheongyak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// V2 저장소 — 경쟁률·당첨가점·지오코딩·실거래·시세. H2(PostgreSQL 모드)에 실제로 넣고 꺼낸다
@SpringBootTest
@ActiveProfiles("test")
class MarketRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final LocalDate SINCE = TODAY.minusDays(365);

    @Autowired
    private NoticeRepository repo;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM cheongyak_rtms_trade").update();
        jdbc.sql("DELETE FROM cheongyak_rtms_month").update();
        jdbc.sql("DELETE FROM cheongyak_house_type").update();
        jdbc.sql("DELETE FROM cheongyak_notice").update();
    }

    private static Geo geo(String lawd, String umd) {
        return new Geo(lawd, lawd + "10100", umd, new BigDecimal("37.2"), new BigDecimal("127.1"), "주소");
    }

    private static CmpetRow cmpet(Notice n, String ty, String reside, int rank, String rate) {
        return CmpetRow.builder().houseManageNo(n.houseManageNo()).pblancNo(n.pblancNo()).houseTy(ty).resideNm(reside)
                .rankCode(rank).suplyHshldco(10).reqCnt(100).cmpetRate(rate == null ? null : new BigDecimal(rate))
                .cmpetText(rate == null ? "미달" : null).rawJson("{}").build();
    }

    private static ScoreRow score(Notice n, String ty, String reside, Integer min) {
        return ScoreRow.builder().houseManageNo(n.houseManageNo()).pblancNo(n.pblancNo()).houseTy(ty).resideNm(reside)
                .minScore(min).maxScore(min == null ? null : min + 10).avgScore(null).rawJson("{}").build();
    }

    private static RtmsTrade trade(String lawd, String ymd, String umd, double area, long amount, int built, String canceled) {
        return RtmsTrade.builder().lawdCd(lawd).dealYmd(ymd).umdNm(umd).aptNm("단지").jibun("1")
                .excluUseAr(BigDecimal.valueOf(area)).dealAmount(amount).floorNo(5).buildYear(built).dealDay(3)
                .canceled(canceled).dealingGbn("중개거래").rawJson("{}").build();
    }

    @Test
    @DisplayName("결과 대상은 발표일이 지난 판정 대상만, 발표일 최신순 — 오피스텔·발표 전·이미 받은 공고는 뺀다")
    void resultTargets() {
        repo.upsertNotice(Fixtures.apt("A1").przwnerDe(TODAY.minusDays(10)).build());
        repo.upsertNotice(Fixtures.remndr("R1").przwnerDe(TODAY.minusDays(2)).build());
        repo.upsertNotice(Fixtures.apt("A2").przwnerDe(TODAY.plusDays(3)).build());
        repo.upsertNotice(Fixtures.apt("O1").kind("URBTY").houseSecd("02").przwnerDe(TODAY.minusDays(5)).build());
        repo.upsertNotice(Fixtures.apt("A3").przwnerDe(TODAY.minusDays(1)).build());
        repo.replaceResults("A3", "A3", List.of(), List.of(), "NONE");

        assertThat(repo.resultTargets(TODAY, SINCE, 10)).extracting(Notice::houseManageNo).containsExactly("R1", "A1");
        assertThat(repo.countResultPending(TODAY, SINCE)).isEqualTo(2);
    }

    @Test
    @DisplayName("결과를 두 번 넣어도 줄이 겹치지 않고, 같은 줄이 두 번 오면 앞의 것만 남는다")
    void replaceResultsTwice() {
        Notice n = Fixtures.apt("A1").przwnerDe(TODAY.minusDays(10)).build();
        repo.upsertNotice(n);
        List<CmpetRow> rows = List.of(cmpet(n, "084.9700A", "해당지역", 1, "12.50"), cmpet(n, "084.9700A", "기타지역", 1, null),
                cmpet(n, "084.9700A", "해당지역", 1, "99.00"));
        repo.replaceResults("A1", "A1", rows, List.of(score(n, "084.9700A", "해당지역", 52)), "OK");
        repo.replaceResults("A1", "A1", rows, List.of(score(n, "084.9700A", "해당지역", 52)), "OK");

        List<CmpetRow> saved = repo.findCmpet(List.of(n)).get(n.key());
        assertThat(saved).hasSize(2);
        assertThat(saved).filteredOn(r -> "해당지역".equals(r.resideNm())).first()
                .extracting(CmpetRow::cmpetRate).isEqualTo(new BigDecimal("12.50"));
        assertThat(repo.findScores(List.of(n)).get(n.key())).extracting(ScoreRow::minScore).containsExactly(52);
        assertThat(repo.findNotice("A1", "A1").orElseThrow().resultStatus()).isEqualTo("OK");
    }

    @Test
    @DisplayName("raw 가 바뀌어도 결과·지오코딩 값은 그대로 — 공고문 값만 비운다")
    void upsertKeepsResultAndGeo() {
        repo.upsertNotice(Fixtures.apt("A1").address("경기도 화성시 반송동 95").build());
        repo.replaceResults("A1", "A1", List.of(), List.of(), "NONE");
        repo.updateGeo("A1", "A1", geo("41597", "반송동"), "OK");

        assertThat(repo.upsertNotice(Fixtures.apt("A1").rawJson("{\"changed\":1}").build())).isEqualTo(2);
        Notice after = repo.findNotice("A1", "A1").orElseThrow();
        assertThat(after.resultStatus()).isEqualTo("NONE");
        assertThat(after).extracting(Notice::geoStatus, Notice::lawdCd, Notice::umdNm).containsExactly("OK", "41597", "반송동");
    }

    @Test
    @DisplayName("지오코딩 대상은 주소 있는 수도권 판정 대상만 — 오피스텔·주소 없음·수도권 밖·이미 한 공고는 뺀다")
    void geoTargets() {
        repo.upsertNotice(Fixtures.apt("A1").address("서울 강동구 강일동 174").build());
        repo.upsertNotice(Fixtures.apt("A2").address(null).build());
        repo.upsertNotice(Fixtures.apt("A3").areaNm("부산").address("부산 해운대구 우동 1").build());
        repo.upsertNotice(Fixtures.apt("O1").kind("URBTY").houseSecd("02").address("서울 마포구 1").build());
        repo.upsertNotice(Fixtures.remndr("R1").areaNm("경기").address("경기도 파주시 동패동 1").build());
        repo.upsertNotice(Fixtures.apt("A4").address("서울 강남구 1").build());
        repo.updateGeo("A4", "A4", null, "FAIL");

        assertThat(repo.geoTargets(10)).extracting(Notice::houseManageNo).containsExactlyInAnyOrder("A1", "R1");
        assertThat(repo.countGeoPending()).isEqualTo(2);
        assertThat(repo.findNotice("A4", "A4").orElseThrow()).extracting(Notice::geoStatus, Notice::lawdCd).containsExactly("FAIL", null);
    }

    @Test
    @DisplayName("실거래를 볼 시군구는 지오코딩된 판정 대상 중 접수가 끝난 지 30일 안인 공고만")
    void rtmsLawdCodes() {
        repo.upsertNotice(Fixtures.apt("A1").rceptEndde(TODAY.minusDays(5)).build());
        repo.upsertNotice(Fixtures.apt("A2").rceptEndde(TODAY.minusDays(60)).build());
        repo.upsertNotice(Fixtures.apt("A3").rceptEndde(null).build());
        repo.updateGeo("A1", "A1", geo("41597", "반송동"), "OK");
        repo.updateGeo("A2", "A2", geo("11680", "역삼동"), "OK");
        repo.updateGeo("A3", "A3", geo("41135", "정자동"), "OK");

        assertThat(repo.rtmsLawdCodes(TODAY.minusDays(30))).containsExactly("41135", "41597");
    }

    @Test
    @DisplayName("한 달치 실거래를 두 번 넣으면 통째로 바뀐다 — 건수 그대로, 받은 시각은 새로")
    void replaceRtmsMonthTwice() {
        List<RtmsTrade> month = List.of(trade("41597", "202609", "반송동", 84.9, 70000, 2024, null),
                trade("41597", "202609", "반송동", 84.9, 72000, 2024, null));
        repo.replaceRtmsMonth("41597", "202609", month, 1);
        LocalDateTime first = repo.rtmsMonthFetchedAt("41597", "202609").orElseThrow();
        repo.replaceRtmsMonth("41597", "202609", month, 1);

        assertThat(jdbc.sql("SELECT COUNT(*) FROM cheongyak_rtms_trade").query(Integer.class).single()).isEqualTo(2);
        assertThat(repo.rtmsMonthFetchedAt("41597", "202609").orElseThrow()).isAfterOrEqualTo(first);
        assertThat(repo.rtmsMonthFetchedAt("41597", "202608")).isEmpty();
    }

    @Test
    @DisplayName("시세 표본은 해제·준공 오래된·면적 밖·기간 밖 거래를 뺀다. 동을 안 주면 시군구 전체")
    void tradeAmounts() {
        repo.replaceRtmsMonth("41597", "202609", List.of(
                trade("41597", "202609", "반송동", 84.9, 70000, 2024, null),
                trade("41597", "202609", "반송동", 84.9, 99999, 2024, "Y"),
                trade("41597", "202609", "반송동", 84.9, 50000, 2010, null),
                trade("41597", "202609", "반송동", 59.9, 50000, 2024, null),
                trade("41597", "202609", "청계동", 82.0, 68000, 2023, null)), 1);
        repo.replaceRtmsMonth("41597", "202409", List.of(trade("41597", "202409", "반송동", 84.9, 40000, 2024, null)), 1);

        BigDecimal min = new BigDecimal("79.97");
        BigDecimal max = new BigDecimal("89.97");
        assertThat(repo.tradeAmounts("41597", "반송동", min, max, 2021, "202511")).containsExactly(70000L);
        assertThat(repo.tradeAmounts("41597", null, min, max, 2021, "202511")).containsExactlyInAnyOrder(70000L, 68000L);
    }

    @Test
    @DisplayName("시세 대상 주택형은 지오코딩된 공고의 전용·분양가가 있는 줄만, 결과는 updateMarket 으로 남는다")
    void marketTargetsAndUpdate() {
        Notice n = Fixtures.apt("A1").rceptEndde(TODAY).build();
        repo.saveNoticeWithTypes(n, () -> List.of(Fixtures.type(n, "01", 84.97).build(),
                Fixtures.type(n, "02", 59.0).exclusiveArea(null).build()));
        repo.updateGeo("A1", "A1", geo("41597", "반송동"), "OK");

        List<MarketTarget> targets = repo.marketTargets(TODAY.minusDays(30));
        assertThat(targets).extracting(MarketTarget::modelNo, MarketTarget::lawdCd, MarketTarget::umdNm)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("01", "41597", "반송동"));

        repo.updateMarket("A1", "A1", "01", 71000L, 4, "DONG");
        HouseType t = repo.findHouseTypes(List.of(n)).get(n.key()).get(0);
        assertThat(t).extracting(HouseType::mktMedian, HouseType::mktCount, HouseType::mktBasis).containsExactly(71000L, 4, "DONG");
    }

    @Test
    @DisplayName("가점 참고치 표본은 APT 민영 해당지역 최저 가점만 — 국민·기타지역·가점 없는 줄은 뺀다")
    void scoreSamples() {
        Notice pri = Fixtures.apt("A1").build();
        Notice nat = Fixtures.apt("A2").houseDtlSecd("03").build();
        repo.upsertNotice(pri);
        repo.upsertNotice(nat);
        repo.updateGeo("A1", "A1", geo("41597", "반송동"), "OK");
        repo.replaceResults("A1", "A1", List.of(), List.of(score(pri, "084A", "해당지역", 52), score(pri, "084A", "기타지역", 60),
                score(pri, "059A", "해당지역", null)), "OK");
        repo.replaceResults("A2", "A2", List.of(), List.of(score(nat, "084A", "해당지역", 40)), "OK");

        assertThat(repo.scoreSamples(SINCE)).extracting(ScoreSample::lawdCd, ScoreSample::areaNm, ScoreSample::minScore)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("41597", "서울", 52));
    }
}
