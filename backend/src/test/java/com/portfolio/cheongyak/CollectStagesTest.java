package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 수집 뒤 단계(결과·지오코딩·실거래·시세). 공고는 저장소에 직접 넣고 공고 API 는 빈 목록을 돌려준다.
// 날짜는 실행하는 날 기준이다 — 단계가 NoticeRepository.now() 로 오늘을 정한다
@SpringBootTest
@ActiveProfiles("test")
class CollectStagesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate TODAY = NoticeRepository.now().toLocalDate();
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final JsonNode CMPET = node("{\"HOUSE_TY\":\"084A\",\"RESIDE_SENM\":\"해당지역\",\"SUBSCRPT_RANK_CODE\":1,"
            + "\"SUPLY_HSHLDCO\":10,\"REQ_CNT\":\"120\",\"CMPET_RATE\":\"12.00\"}");
    private static final JsonNode SCORE = node("{\"HOUSE_TY\":\"084A\",\"RESIDE_SENM\":\"해당지역\",\"LWET_SCORE\":\"52\"}");

    @Autowired
    private CollectService service;

    @Autowired
    private NoticeRepository repo;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private ApplyhomeClient client;

    @MockitoBean
    private NoticeDocClient docClient;

    @MockitoBean
    private JusoGeoClient geoClient;

    @MockitoBean
    private RtmsClient rtmsClient;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM cheongyak_rtms_trade").update();
        jdbc.sql("DELETE FROM cheongyak_rtms_month").update();
        jdbc.sql("DELETE FROM cheongyak_house_type").update();
        jdbc.sql("DELETE FROM cheongyak_notice").update();
        jdbc.sql("DELETE FROM cheongyak_collect_run").update();
        ReflectionTestUtils.setField(service, "worker", (Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "sleeper", (NoticeDocClient.Sleeper) ms -> { });
        ReflectionTestUtils.setField(service, "resultBatch", 150);
        ReflectionTestUtils.setField(service, "geoBatch", 200);
        ReflectionTestUtils.setField(service, "rtmsBatch", 300);
        ReflectionTestUtils.setField(service, "rtmsMonths", 12);
        when(docClient.fetch(anyString(), anyString())).thenReturn(NoticeDoc.failed("NO_LINK"));
    }

    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }

    private long running() {
        return repo.insertRun(CollectRun.builder().startedAt(NoticeRepository.now()).status(CollectRun.RUNNING)
                .triggerSrc("manual").sinceDate(TODAY.minusDays(45)).build());
    }

    private CollectRun run() {
        return service.run(running(), TODAY.minusDays(45));
    }

    private void announced(Notice.NoticeBuilder b, int daysAgo) {
        repo.upsertNotice(b.rcritPblancDe(TODAY.minusDays(daysAgo + 20)).przwnerDe(TODAY.minusDays(daysAgo)).build());
    }

    @Test
    @DisplayName("발표 지난 APT 는 경쟁률·당첨가점 2콜, 무순위는 경쟁률 1콜 — 둘 다 OK")
    void resultsForAptAndRemndr() {
        announced(Fixtures.apt("A1"), 10);
        announced(Fixtures.remndr("R1"), 2);
        when(client.fetchCmpet(any(), anyString(), anyString())).thenReturn(List.of(CMPET));
        when(client.fetchScores(anyString(), anyString())).thenReturn(List.of(SCORE));

        CollectRun r = run();

        verify(client).fetchCmpet(eq(ApplyhomeKind.APT), eq("A1"), eq("A1"));
        verify(client).fetchCmpet(eq(ApplyhomeKind.REMNDR), eq("R1"), eq("R1"));
        verify(client, times(1)).fetchScores(anyString(), anyString());
        assertThat(r).extracting(CollectRun::status, CollectRun::resultFetched, CollectRun::resultOk, CollectRun::resultPending)
                .containsExactly(CollectRun.OK, 2, 2, 0);
        Notice a1 = repo.findNotice("A1", "A1").orElseThrow();
        assertThat(repo.findScores(List.of(a1)).get(a1.key())).extracting(ScoreRow::minScore).containsExactly(52);
    }

    @Test
    @DisplayName("결과가 비면 발표 14일 안은 다음에 다시(상태 그대로), 지나면 NONE")
    void emptyResultGrace() {
        announced(Fixtures.apt("A1"), 3);
        announced(Fixtures.apt("A2"), 20);

        CollectRun r = run();

        assertThat(repo.findNotice("A1", "A1").orElseThrow().resultStatus()).isNull();
        assertThat(repo.findNotice("A2", "A2").orElseThrow().resultStatus()).isEqualTo("NONE");
        assertThat(r).extracting(CollectRun::resultFetched, CollectRun::resultOk, CollectRun::resultPending).containsExactly(2, 0, 1);
    }

    @Test
    @DisplayName("결과는 실행당 batch-size 공고만")
    void resultBatch() {
        announced(Fixtures.apt("A1"), 3);
        announced(Fixtures.apt("A2"), 4);
        ReflectionTestUtils.setField(service, "resultBatch", 1);
        assertThat(run().resultFetched()).isEqualTo(1);
    }

    @Test
    @DisplayName("결과 단계가 실패해도 수집은 OK 이고 다음 단계(지오코딩)는 돈다")
    void stageFailureContinues() {
        announced(Fixtures.apt("A1").address("서울 강동구 강일동 174"), 10);
        when(client.fetchCmpet(any(), anyString(), anyString()))
                .thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "x"));
        when(geoClient.enabled()).thenReturn(true);
        when(geoClient.geocode(anyString())).thenReturn(Optional.empty());

        CollectRun r = run();

        assertThat(r.status()).isEqualTo(CollectRun.OK);
        assertThat(r).extracting(CollectRun::resultOk, CollectRun::geoFetched).containsExactly(0, 1);
    }

    @Test
    @DisplayName("지오코딩 — 찾은 공고는 법정동코드가 차고, 못 찾은 공고는 FAIL")
    void geocodes() {
        repo.upsertNotice(Fixtures.apt("A1").address("경기도 화성시 반송동 95").build());
        repo.upsertNotice(Fixtures.apt("A2").address("어딘가").build());
        when(geoClient.enabled()).thenReturn(true);
        when(geoClient.geocode(eq("경기도 화성시 반송동 95")))
                .thenReturn(Optional.of(new Geo("41597", "4159710800", "반송동", BigDecimal.ONE, BigDecimal.ONE, "a")));
        when(geoClient.geocode(eq("어딘가"))).thenReturn(Optional.empty());

        CollectRun r = run();

        assertThat(r).extracting(CollectRun::geoFetched, CollectRun::geoOk, CollectRun::geoPending).containsExactly(2, 1, 0);
        assertThat(repo.findNotice("A1", "A1").orElseThrow()).extracting(Notice::geoStatus, Notice::lawdCd).containsExactly("OK", "41597");
        assertThat(repo.findNotice("A2", "A2").orElseThrow().geoStatus()).isEqualTo("FAIL");
    }

    @Test
    @DisplayName("카카오 키가 없으면 지오코딩을 건너뛰고 남은 수만 센다")
    void geoDisabled() {
        repo.upsertNotice(Fixtures.apt("A1").address("서울 강남구 1").build());
        when(geoClient.enabled()).thenReturn(false);

        CollectRun r = run();

        verify(geoClient, never()).geocode(anyString());
        assertThat(r).extracting(CollectRun::geoFetched, CollectRun::geoPending).containsExactly(0, 1);
    }

    private void geocoded(String hmn) {
        repo.upsertNotice(Fixtures.apt(hmn).address("경기도 화성시 반송동 95").rceptEndde(null).build());
        repo.updateGeo(hmn, hmn, new Geo("41597", "4159710800", "반송동", BigDecimal.ONE, BigDecimal.ONE, "a"), "OK");
    }

    @Test
    @DisplayName("실거래 — 이미 받은 지난 달은 건너뛰고 이번 달·지난달만 다시 받는다")
    void tradesSkipFetchedMonths() {
        geocoded("A1");
        YearMonth now = YearMonth.from(TODAY);
        for (int i = 2; i < 12; i++) {
            repo.replaceRtmsMonth("41597", now.minusMonths(i).format(YYYYMM), List.of(), 1);
        }
        when(rtmsClient.fetchMonth(anyString(), anyString())).thenReturn(new RtmsClient.Fetched(List.of(), 1));

        CollectRun r = run();

        verify(rtmsClient).fetchMonth("41597", now.format(YYYYMM));
        verify(rtmsClient).fetchMonth("41597", now.minusMonths(1).format(YYYYMM));
        assertThat(r).extracting(CollectRun::rtmsCalls, CollectRun::rtmsPending).containsExactly(2, 0);
    }

    @Test
    @DisplayName("실거래 호출이 batch-size 에 닿으면 멈추고 남은 달 수를 센다")
    void tradesBatch() {
        geocoded("A1");
        ReflectionTestUtils.setField(service, "rtmsMonths", 3);
        ReflectionTestUtils.setField(service, "rtmsBatch", 1);
        when(rtmsClient.fetchMonth(anyString(), anyString())).thenReturn(new RtmsClient.Fetched(List.of(), 1));

        CollectRun r = run();

        assertThat(r).extracting(CollectRun::rtmsCalls, CollectRun::rtmsPending).containsExactly(1, 2);
    }

    private static RtmsTrade trade(String umd, double area, long amount, int built, String canceled) {
        return RtmsTrade.builder().lawdCd("41597").dealYmd(YearMonth.from(TODAY).format(YYYYMM)).umdNm(umd).aptNm("단지")
                .excluUseAr(BigDecimal.valueOf(area)).dealAmount(amount).buildYear(built).canceled(canceled).rawJson("{}").build();
    }

    @Test
    @DisplayName("시세 — 같은 동 신축 ±5㎡ 거래 3건의 중앙값, 해제 거래는 뺀다")
    void marketFromSameDong() {
        geocoded("A1");
        Notice n = repo.findNotice("A1", "A1").orElseThrow();
        repo.replaceHouseTypes("A1", "A1", List.of(Fixtures.type(n, "01", 84.97).build()));
        int built = TODAY.getYear() - 1;
        repo.replaceRtmsMonth("41597", YearMonth.from(TODAY).format(YYYYMM), List.of(
                trade("반송동", 84.9, 70000, built, null), trade("반송동", 83.0, 72000, built, null),
                trade("반송동", 86.0, 75000, built, null), trade("반송동", 84.9, 150000, built, "Y")), 1);
        // 실거래 단계가 이번 달을 다시 받아 지우지 않게 막 받은 것으로 둔다
        when(rtmsClient.fetchMonth(anyString(), anyString())).thenReturn(new RtmsClient.Fetched(List.of(), 1));
        ReflectionTestUtils.setField(service, "rtmsMonths", 1);
        ReflectionTestUtils.setField(service, "rtmsBatch", 0);

        CollectRun r = run();

        HouseType t = repo.findHouseTypes(List.of(n)).get(n.key()).get(0);
        assertThat(t).extracting(HouseType::mktMedian, HouseType::mktCount, HouseType::mktBasis).containsExactly(72000L, 3, "DONG");
        assertThat(r.mktUpdated()).isEqualTo(1);
    }
}
