package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import com.portfolio.service.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 청약홈 API 를 가짜 서버로 바꿔 페이지 넘기기·필터 인코딩·실패 분류를 본다
class ApplyhomeClientTest {

    private StubServer stub;
    private ApplyhomeClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        client = new ApplyhomeClient();
        ReflectionTestUtils.setField(client, "serviceKey", "abc+def/ghi==");
        ReflectionTestUtils.setField(client, "baseUrl", stub.url());
        ReflectionTestUtils.setField(client, "cmpetBaseUrl", stub.url() + "/cmpet");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static String page(int from, int count) {
        String rows = IntStream.range(from, from + count)
                .mapToObj(i -> "{\"HOUSE_MANAGE_NO\":\"" + i + "\"}").collect(Collectors.joining(","));
        return "{\"page\":1,\"perPage\":100,\"currentCount\":" + count + ",\"data\":[" + rows + "]}";
    }

    @Test
    @DisplayName("currentCount 가 100 미만인 장에서 멈춘다 — 100건 + 3건 = 103건, 필터와 키가 인코딩돼 나간다")
    void pagesUntilShortPage() {
        stub.on("/getAPTLttotPblancDetail", q -> new Object[]{200, q.contains("page=1&") ? page(0, 100) : page(100, 3)});

        List<JsonNode> got = client.fetchDetails(ApplyhomeKind.APT, LocalDate.of(2026, 1, 1));

        assertThat(got).hasSize(103);
        assertThat(stub.requests()).hasSize(2);
        assertThat(stub.requests().get(0))
                .contains("cond%5BRCRIT_PBLANC_DE%3A%3AGTE%5D=2026-01-01")
                .contains("serviceKey=abc%2Bdef%2Fghi%3D%3D");
        assertThat(client.callCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("공공지원 민간임대는 공고일을 YYYYMMDD 로 보낸다")
    void compactDateKinds() {
        stub.on("/getPblPvtRentLttotPblancDetail", q -> new Object[]{200, page(0, 1)});
        client.fetchDetails(ApplyhomeKind.PBLPVT, LocalDate.of(2026, 1, 1));
        assertThat(stub.requests().get(0)).contains("GTE%5D=20260101");
    }

    @Test
    @DisplayName("200 인데 HTML 이면 UPSTREAM_ERROR")
    void htmlBody() {
        stub.on("/getAPTLttotPblancDetail", q -> new Object[]{200, "<html>점검 중</html>"});
        assertThatThrownBy(() -> client.fetchDetails(ApplyhomeKind.APT, LocalDate.of(2026, 1, 1)))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR"));
    }

    @Test
    @DisplayName("키가 비면 부르지 않고 KEY_NOT_SET")
    void noKey() {
        ReflectionTestUtils.setField(client, "serviceKey", "");
        assertThatThrownBy(() -> client.fetchHouseTypes(ApplyhomeKind.APT, "1"))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.getCode()).isEqualTo("KEY_NOT_SET"));
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    @DisplayName("주택형은 공고번호로 걸러 한 장만 받는다. data 가 없으면 빈 목록")
    void houseTypes() {
        stub.on("/getRemndrLttotPblancMdl", q -> new Object[]{200, q.contains("PBLANC_NO") ? page(0, 2) : "{}"});
        assertThat(client.fetchHouseTypes(ApplyhomeKind.REMNDR, "2026910270")).hasSize(2);
        assertThat(stub.requests().get(0)).contains("cond%5BPBLANC_NO%3A%3AEQ%5D=2026910270");
    }

    @Test
    @DisplayName("날짜 — YYYY-MM-DD·YYYYMMDD 둘 다 읽고, 빈 값·낯선 형식은 null")
    void dates() {
        assertThat(ApplyhomeDates.parse("2026-10-23")).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(ApplyhomeDates.parse("20261015")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(ApplyhomeDates.parse(" ")).isNull();
        assertThat(ApplyhomeDates.parse("2026.10.23")).isNull();
    }

    @Test
    @DisplayName("경쟁률은 공고 키 두 개로 거르고, 숫자 아닌 경쟁률은 표기만 남긴다")
    void cmpetRows() {
        stub.on("/cmpet/getAPTLttotPblancCmpet", q -> new Object[]{200, "{\"data\":["
                + "{\"HOUSE_TY\":\"084.9700A\",\"RESIDE_SENM\":\"해당지역\",\"SUBSCRPT_RANK_CODE\":1,"
                + "\"SUPLY_HSHLDCO\":40,\"REQ_CNT\":\"1,250\",\"CMPET_RATE\":\"31.25\"},"
                + "{\"HOUSE_TY\":\"084.9700A\",\"RESIDE_SENM\":\"기타지역\",\"SUBSCRPT_RANK_CODE\":1,"
                + "\"SUPLY_HSHLDCO\":40,\"REQ_CNT\":\"0\",\"CMPET_RATE\":\"-\"}]}"});

        List<CmpetRow> rows = client.fetchCmpet(ApplyhomeKind.APT, "2026000502", "2026000502").stream()
                .map(d -> NoticeMapper.toCmpet("2026000502", "2026000502", d)).toList();

        assertThat(stub.requests().get(0))
                .contains("cond%5BHOUSE_MANAGE_NO%3A%3AEQ%5D=2026000502")
                .contains("cond%5BPBLANC_NO%3A%3AEQ%5D=2026000502");
        assertThat(rows).extracting(CmpetRow::resideNm, CmpetRow::rankCode, CmpetRow::reqCnt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("해당지역", 1, 1250),
                        org.assertj.core.groups.Tuple.tuple("기타지역", 1, 0));
        assertThat(rows.get(0).cmpetRate()).isEqualByComparingTo("31.25");
        assertThat(rows.get(1)).extracting(CmpetRow::cmpetRate, CmpetRow::cmpetText).containsExactly(null, "-");
    }

    @Test
    @DisplayName("무순위 경쟁률은 순위 칸이 없어 0, 당첨가점은 최저·최고·평균을 읽는다")
    void remndrCmpetAndScores() {
        stub.on("/cmpet/getRemndrLttotPblancCmpet", q -> new Object[]{200,
                "{\"data\":[{\"HOUSE_TY\":\"059A\",\"SUPLY_HSHLDCO\":2,\"REQ_CNT\":\"3021\",\"CMPET_RATE\":\"1510.5\"}]}"});
        stub.on("/cmpet/getAptLttotPblancScore", q -> new Object[]{200,
                "{\"data\":[{\"HOUSE_TY\":\"084A\",\"RESIDE_SENM\":\"해당지역\",\"LWET_SCORE\":\"52\",\"TOP_SCORE\":69,\"AVRG_SCORE\":\"58.31\"}]}"});

        CmpetRow c = NoticeMapper.toCmpet("R", "R", client.fetchCmpet(ApplyhomeKind.REMNDR, "R", "R").get(0));
        ScoreRow sc = NoticeMapper.toScore("A", "A", client.fetchScores("A", "A").get(0));

        assertThat(c.rankCode()).isZero();
        assertThat(c.resideNm()).isEqualTo("-");
        assertThat(sc).extracting(ScoreRow::minScore, ScoreRow::maxScore).containsExactly(52, 69);
        assertThat(sc.avgScore()).isEqualByComparingTo("58.31");
    }

    @Test
    @DisplayName("당첨가점 최고가 0 인 행과 「-」 행은 세 값 다 비운다")
    void zeroAndDashScores() {
        stub.on("/cmpet/getAptLttotPblancScore", q -> new Object[]{200, "{\"data\":["
                + "{\"HOUSE_TY\":\"084A\",\"RESIDE_SENM\":\"기타지역\",\"LWET_SCORE\":\"0\",\"TOP_SCORE\":\"0\",\"AVRG_SCORE\":\"0\"},"
                + "{\"HOUSE_TY\":\"084A\",\"RESIDE_SENM\":\"해당지역\",\"LWET_SCORE\":\"-\",\"TOP_SCORE\":\"-\",\"AVRG_SCORE\":\"-\"}]}"});

        List<ScoreRow> rows = client.fetchScores("A", "A").stream().map(d -> NoticeMapper.toScore("A", "A", d)).toList();

        assertThat(rows).extracting(ScoreRow::minScore, ScoreRow::maxScore, ScoreRow::avgScore)
                .containsOnly(org.assertj.core.groups.Tuple.tuple(null, null, null));
    }

    @Test
    @DisplayName("결과가 아직 없으면 빈 목록, 판정 대상 아닌 종류는 부르지 않는다")
    void emptyAndUnsupported() {
        stub.on("/cmpet/getAPTLttotPblancCmpet", q -> new Object[]{200, "{\"currentCount\":0,\"data\":[]}"});
        assertThat(client.fetchCmpet(ApplyhomeKind.APT, "A", "A")).isEmpty();
        assertThatThrownBy(() -> client.fetchCmpet(ApplyhomeKind.URBTY, "A", "A")).isInstanceOf(IllegalArgumentException.class);
    }
}
