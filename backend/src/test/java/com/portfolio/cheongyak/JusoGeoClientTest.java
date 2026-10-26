package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import com.portfolio.service.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 도로명주소 검색을 가짜 서버로 바꿔 주소 정리·후보 순서·법정동코드 추출·오류 코드 분류를 본다
class JusoGeoClientTest {

    private static final String BANSONG = "{\"results\":{\"common\":{\"errorCode\":\"0\",\"errorMessage\":\"정상\",\"totalCount\":\"1\"},"
            + "\"juso\":[{\"admCd\":\"4159710800\",\"emdNm\":\"반송동\",\"jibunAddr\":\"경기도 화성특례시 동탄구 반송동 95\"}]}}";
    private static final String EMPTY = "{\"results\":{\"common\":{\"errorCode\":\"0\",\"errorMessage\":\"정상\",\"totalCount\":\"0\"},\"juso\":[]}}";

    private StubServer stub;
    private JusoGeoClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        client = new JusoGeoClient();
        ReflectionTestUtils.setField(client, "jusoKey", "k");
        ReflectionTestUtils.setField(client, "jusoUrl", stub.url() + "/addrLinkApi.do");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "경기도 화성특례시 동탄구 반송동 95번지[동탄(1) M1-1-2블록]|경기도 화성특례시 동탄구 반송동 95",
        "경기도 시흥시 거모동, 군자동 일원 시흥거모 공공주택지구 내 S-1블록|경기도 시흥시 거모동",
        "경기도 화성시 만세구 향남읍 상신리 산 13-13 일원|경기도 화성시 만세구 향남읍 상신리 산 13-13",
        "인천광역시 계양구 동양동 420-3번지 일원 (인천계양 테크노밸리 공공주택지구 AC4블록)|인천광역시 계양구 동양동 420-3",
        "서울특별시 동작구 노량진동 312-75번지 일대|서울특별시 동작구 노량진동 312-75",
        "서울특별시 양천구 신월동 118-14외 2필지 일대(덕화주택 가로주택정비사업조합)|서울특별시 양천구 신월동 118-14",
    })
    @DisplayName("공고 주소 문구에서 설명·다른 동·일원·일대·외 n필지를 떼고 번지만 남긴다")
    void cleanAddress(String raw, String expected) {
        assertThat(JusoGeoClient.cleanAddress(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("후보는 괄호 밖 주소 → 그 읍면동 → 괄호 안 주소 → 그 읍면동 순서, 겹치면 한 번만")
    void candidates() {
        assertThat(JusoGeoClient.candidates("경기도 김포시 풍무역세권도시개발사업 B1BL(김포시 사우동 167-1번지)"))
                .containsExactly("경기도 김포시 풍무역세권도시개발사업 B1BL", "김포시 사우동 167-1", "김포시 사우동");
        assertThat(JusoGeoClient.candidates("인천광역시 검단구 불로동 검단신도시 AA22블록"))
                .containsExactly("인천광역시 검단구 불로동 검단신도시 AA22블록", "인천광역시 검단구 불로동");
        assertThat(JusoGeoClient.candidates("경기도 양주시 회천지구 A10-1BL")).containsExactly("경기도 양주시 회천지구 A10-1BL");
    }

    @Test
    @DisplayName("주소가 맞으면 법정동코드 앞 5자리가 시군구 코드, emdNm 이 동. 좌표는 비운다")
    void byAddress() {
        stub.on("/addrLinkApi.do", q -> new Object[]{200, BANSONG});

        Geo g = client.geocode("경기도 화성특례시 동탄구 반송동 95번지[동탄(1) M1-1-2블록]").orElseThrow();

        assertThat(g).extracting(Geo::lawdCd, Geo::bcode, Geo::umdNm).containsExactly("41597", "4159710800", "반송동");
        assertThat(g.lat()).isNull();
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    @DisplayName("번지가 안 맞으면 읍면동까지로 줄여 다시 찾는다")
    void dongFallback() {
        String dongOnly = "keyword=" + URLEncoder.encode("경기도 화성특례시 동탄구 반송동", StandardCharsets.UTF_8) + "&";
        stub.on("/addrLinkApi.do", q -> new Object[]{200, q.contains(dongOnly) ? BANSONG : EMPTY});

        assertThat(client.geocode("경기도 화성특례시 동탄구 반송동 9999번지 일원")).map(Geo::lawdCd).contains("41597");
        assertThat(client.callCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("후보가 다 비거나 검색어 오류 코드면 empty, 키가 없으면 enabled false")
    void emptyAndDisabled() {
        stub.on("/addrLinkApi.do", q -> new Object[]{200, EMPTY});
        assertThat(client.geocode("경기도 양주시 회천지구 A10-1BL")).isEmpty();

        stub.on("/addrLinkApi.do", q -> new Object[]{200,
            "{\"results\":{\"common\":{\"errorCode\":\"E0013\",\"errorMessage\":\"SQL 예약어\"},\"juso\":null}}"});
        assertThat(client.geocode("서울특별시 강남구 역삼동 1")).isEmpty();

        ReflectionTestUtils.setField(client, "jusoKey", "");
        assertThat(client.enabled()).isFalse();
    }

    @Test
    @DisplayName("승인키 오류 코드면 JUSO_ERROR 로 단계를 멈춘다 — 키 값은 메시지에 안 들어간다")
    void unauthorized() {
        stub.on("/addrLinkApi.do", q -> new Object[]{200,
            "{\"results\":{\"common\":{\"errorCode\":\"E0001\",\"errorMessage\":\"승인되지 않은 KEY 입니다.\"},\"juso\":null}}"});
        assertThatThrownBy(() -> client.geocode("서울특별시 강남구 역삼동 1"))
                .isInstanceOf(UpstreamException.class).hasMessageContaining("E0001").hasMessageNotContaining("confmKey");
    }

    @Test
    @DisplayName("HTTP 500 이면 JUSO_ERROR")
    void httpError() {
        stub.on("/addrLinkApi.do", q -> new Object[]{500, "{}"});
        assertThatThrownBy(() -> client.geocode("서울특별시 강남구 역삼동 1"))
                .isInstanceOf(UpstreamException.class).hasMessageContaining("HTTP 500");
    }
}
