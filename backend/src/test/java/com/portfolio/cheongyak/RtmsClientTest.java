package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import com.portfolio.service.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 국토부 실거래 API 를 가짜 서버로 바꿔 XML 읽기·해제거래 표시·페이지 넘기기·오류 봉투를 본다.
// 가짜 서버는 Content-Type 을 JSON 으로 주지만 클라이언트는 Content-Type 을 보지 않는다
class RtmsClientTest {

    private StubServer stub;
    private RtmsClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        client = new RtmsClient();
        ReflectionTestUtils.setField(client, "serviceKey", "abc+def/ghi==");
        ReflectionTestUtils.setField(client, "baseUrl", stub.url());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private static String item(String umd, String amount, String cdeal) {
        return "<item><aptNm>동탄레이크자연앤푸르지오</aptNm><umdNm> " + umd + "</umdNm><jibun>95</jibun>"
                + "<excluUseAr>84.9718</excluUseAr><dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>12</dealDay>"
                + "<dealAmount>" + amount + "</dealAmount><floor>15</floor><buildYear>2024</buildYear>"
                + "<cdealType>" + cdeal + "</cdealType><dealingGbn>중개거래</dealingGbn></item>";
    }

    private static String ok(int total, String items) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>"
                + "<body><items>" + items + "</items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>" + total + "</totalCount></body></response>";
    }

    @Test
    @DisplayName("거래 두 건 — 금액 쉼표를 떼고, 해제 신고 거래는 canceled = Y, 키는 한 번 인코딩돼 나간다")
    void readsItems() {
        stub.on("/getRTMSDataSvcAptTradeDev", q -> new Object[]{200, ok(2, item("반송동", "71,500", "") + item("반송동", "99,000", "O"))});

        RtmsClient.Fetched f = client.fetchMonth("41597", "202609");

        assertThat(f.calls()).isEqualTo(1);
        assertThat(f.trades()).extracting(RtmsTrade::dealAmount, RtmsTrade::canceled)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(71500L, null), org.assertj.core.groups.Tuple.tuple(99000L, "Y"));
        RtmsTrade t = f.trades().get(0);
        assertThat(t).extracting(RtmsTrade::lawdCd, RtmsTrade::dealYmd, RtmsTrade::umdNm, RtmsTrade::buildYear, RtmsTrade::floorNo)
                .containsExactly("41597", "202609", "반송동", 2024, 15);
        assertThat(t.excluUseAr()).isEqualByComparingTo("84.9718");
        assertThat(t.rawJson()).contains("\"aptNm\":\"동탄레이크자연앤푸르지오\"");
        assertThat(stub.requests().get(0)).contains("serviceKey=abc%2Bdef%2Fghi%3D%3D").contains("LAWD_CD=41597").contains("DEAL_YMD=202609");
    }

    @Test
    @DisplayName("totalCount 가 1000 을 넘으면 pageNo 2 까지 부른다")
    void pagesByTotalCount() {
        String thousand = IntStream.range(0, 1000).mapToObj(i -> item("반송동", "70,000", "")).collect(Collectors.joining());
        stub.on("/getRTMSDataSvcAptTradeDev", q -> new Object[]{200, q.contains("pageNo=1") ? ok(1001, thousand) : ok(1001, item("청계동", "60,000", ""))});

        RtmsClient.Fetched f = client.fetchMonth("41597", "202609");

        assertThat(f.trades()).hasSize(1001);
        assertThat(f.calls()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과 코드가 정상이 아니면 RTMS_ERROR")
    void badResultCode() {
        stub.on("/getRTMSDataSvcAptTradeDev", q -> new Object[]{200,
                "<response><header><resultCode>03</resultCode><resultMsg>NO_DATA</resultMsg></header></response>"});
        assertThatThrownBy(() -> client.fetchMonth("41597", "202609")).isInstanceOf(UpstreamException.class).hasMessageContaining("03");
    }

    @Test
    @DisplayName("키 오류 봉투(cmmMsgHeader)도 RTMS_ERROR — 키 값은 메시지에 없다")
    void keyError() {
        stub.on("/getRTMSDataSvcAptTradeDev", q -> new Object[]{200, "<OpenAPI_ServiceResponse><cmmMsgHeader>"
                + "<errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>"});
        assertThatThrownBy(() -> client.fetchMonth("41597", "202609"))
                .isInstanceOf(UpstreamException.class).hasMessageContaining("30").hasMessageNotContaining("abc");
    }

    @Test
    @DisplayName("키가 없으면 KEY_NOT_SET, 본문이 XML 이 아니면 RTMS_ERROR")
    void noKeyAndNotXml() {
        stub.on("/getRTMSDataSvcAptTradeDev", q -> new Object[]{200, "<html>점검 중"});
        assertThatThrownBy(() -> client.fetchMonth("41597", "202609")).hasMessageContaining("XML");
        ReflectionTestUtils.setField(client, "serviceKey", "");
        assertThatThrownBy(() -> client.fetchMonth("41597", "202609")).isInstanceOf(UpstreamException.class)
                .extracting("code").isEqualTo("KEY_NOT_SET");
    }
}
