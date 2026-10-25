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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 카카오 주소 검색을 가짜 서버로 바꿔 주소 정리·키워드 대체·법정동코드 추출을 본다
class KakaoGeoClientTest {

    private static final String BANSONG = "{\"documents\":[{\"address_name\":\"경기 화성시 동탄구 반송동 95\",\"x\":\"127.0712\",\"y\":\"37.2001\","
            + "\"address\":{\"b_code\":\"4159710800\",\"region_3depth_name\":\"반송동\"}}]}";
    private static final String EMPTY = "{\"documents\":[]}";

    private StubServer stub;
    private KakaoGeoClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        client = new KakaoGeoClient();
        ReflectionTestUtils.setField(client, "kakaoKey", "k");
        ReflectionTestUtils.setField(client, "addressUrl", stub.url() + "/address.json");
        ReflectionTestUtils.setField(client, "keywordUrl", stub.url() + "/keyword.json");
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
        "서울특별시 강동구 강일동 174 일원(고덕강일공공주택지구 3단지)|서울특별시 강동구 강일동 174",
    })
    @DisplayName("공고 주소 문구에서 설명·다른 동·일원을 떼고 번지만 남긴다")
    void cleanAddress(String raw, String expected) {
        assertThat(KakaoGeoClient.cleanAddress(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("주소 검색이 맞으면 법정동코드 앞 5자리가 시군구 코드, 3단계 이름이 동")
    void byAddress() {
        stub.on("/address.json", q -> new Object[]{200, BANSONG});

        Geo g = client.geocode("경기도 화성특례시 동탄구 반송동 95번지[동탄(1) M1-1-2블록]", "동탄").orElseThrow();

        assertThat(g).extracting(Geo::lawdCd, Geo::bcode, Geo::umdNm).containsExactly("41597", "4159710800", "반송동");
        assertThat(g.lat()).isEqualByComparingTo("37.2001");
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    @DisplayName("주소가 안 맞으면 단지명 키워드 검색 → 그 주소로 다시 주소 검색")
    void keywordFallback() {
        stub.on("/address.json", q -> new Object[]{200, q.contains("%EB%B0%98%EC%86%A1") ? BANSONG : EMPTY});
        stub.on("/keyword.json", q -> new Object[]{200, "{\"documents\":[{\"address_name\":\"경기 화성시 동탄구 반송동 95\"}]}"});

        Optional<Geo> g = client.geocode("경기도 화성시 동탄2 신도시 C-1블록", "동탄 레이크 자이");

        assertThat(g).map(Geo::lawdCd).contains("41597");
        assertThat(stub.requests()).hasSize(3);
        assertThat(client.callCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("주소·키워드 둘 다 비면 empty, 키가 없으면 enabled false")
    void emptyAndDisabled() {
        stub.on("/address.json", q -> new Object[]{200, EMPTY});
        stub.on("/keyword.json", q -> new Object[]{200, EMPTY});
        assertThat(client.geocode("어딘가", "없는 단지")).isEmpty();

        ReflectionTestUtils.setField(client, "kakaoKey", "");
        assertThat(client.enabled()).isFalse();
    }

    @Test
    @DisplayName("카카오가 401 이면 KAKAO_ERROR — 키 값은 메시지에 안 들어간다")
    void unauthorized() {
        stub.on("/address.json", q -> new Object[]{401, "{}"});
        assertThatThrownBy(() -> client.geocode("서울 강남구 1", null))
                .isInstanceOf(UpstreamException.class).hasMessageContaining("HTTP 401").hasMessageNotContaining("KakaoAK");
    }
}
