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
}
