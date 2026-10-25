package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 국토교통부 아파트 매매 실거래가 상세(data.go.kr 15126468). 시군구(LAWD_CD 5자리) × 계약월(YYYYMM) 단위로 받고
 * 응답은 XML 이다 — 새 의존성 없이 JDK DOM 으로 읽는다. 키는 청약홈과 같은 data.go.kr 키(Decoding)를 한 번 인코딩한다.
 * 해제 신고된 거래(cdealType 있음)도 받아 두고 canceled = Y 로 표시한다. 시세 계산이 뺀다.
 */
@Slf4j
@Service
public class RtmsClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(30);
    private static final int ROWS = 1000;
    // 한 시군구 한 달이 만 건을 넘는 일은 없다. 넘으면 응답이 이상한 것으로 본다
    private static final int MAX_PAGES = 10;
    private static final Set<String> OK_CODES = Set.of("00", "000");

    @Value("${data.go.kr.key:}")
    private String serviceKey;

    // 테스트가 가짜 서버로 갈아끼운다
    @Value("${cheongyak.rtms.base-url:https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev}")
    private String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** 받은 거래와 그 달을 받는 데 쓴 호출 수 */
    public record Fetched(List<RtmsTrade> trades, int calls) {
    }

    /** 한 (시군구, 계약월) 의 거래 전부. totalCount 가 한 장을 넘으면 pageNo 를 올린다 */
    public Fetched fetchMonth(String lawdCd, String dealYmd) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "KEY_NOT_SET", "DATA_GO_KR_KEY 가 없다");
        }
        List<RtmsTrade> out = new ArrayList<>();
        int calls = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            Document doc = call(lawdCd, dealYmd, page);
            calls++;
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                out.add(toTrade(lawdCd, dealYmd, (Element) items.item(i)));
            }
            int total = intOf(text(doc.getDocumentElement(), "totalCount"));
            if (items.getLength() == 0 || page * ROWS >= total) {
                return new Fetched(out, calls);
            }
        }
        throw new UpstreamException(HttpStatus.BAD_GATEWAY, "RTMS_ERROR", "실거래 API 페이지가 " + MAX_PAGES + "장을 넘었다");
    }

    private Document call(String lawdCd, String dealYmd, int page) {
        String url = baseUrl + "/getRTMSDataSvcAptTradeDev?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&LAWD_CD=" + lawdCd + "&DEAL_YMD=" + dealYmd + "&numOfRows=" + ROWS + "&pageNo=" + page;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(READ_TIMEOUT).GET().build();
        HttpResponse<String> res;
        try {
            res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("실거래 API {} {} 연결 실패 - {}", lawdCd, dealYmd, e.getClass().getSimpleName());
            throw rtmsError("연결 실패");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw rtmsError("중단");
        }
        if (res.statusCode() != 200) {
            throw rtmsError("HTTP " + res.statusCode());
        }
        Document doc = parse(res.body());
        Element root = doc.getDocumentElement();
        // 키·쿼터 오류는 다른 봉투(cmmMsgHeader)로 온다
        String reason = text(root, "returnReasonCode");
        if (reason != null) {
            throw rtmsError(reason + " " + nvl(text(root, "errMsg")));
        }
        String code = text(root, "resultCode");
        if (code == null || !OK_CODES.contains(code)) {
            throw rtmsError(nvl(code) + " " + nvl(text(root, "resultMsg")));
        }
        return doc;
    }

    private RtmsTrade toTrade(String lawdCd, String dealYmd, Element item) {
        Map<String, String> raw = new LinkedHashMap<>();
        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                raw.put(c.getNodeName(), c.getTextContent().trim());
            }
        }
        String cdeal = raw.getOrDefault("cdealType", "");
        return RtmsTrade.builder().lawdCd(lawdCd).dealYmd(dealYmd)
                .umdNm(blankToNull(raw.get("umdNm"))).aptNm(blankToNull(raw.get("aptNm"))).jibun(blankToNull(raw.get("jibun")))
                .excluUseAr(decimal(raw.get("excluUseAr")))
                .dealAmount(longOf(raw.get("dealAmount")))
                .floorNo(intOrNull(raw.get("floor"))).buildYear(intOrNull(raw.get("buildYear"))).dealDay(intOrNull(raw.get("dealDay")))
                .canceled(cdeal.isBlank() ? null : "Y")
                .dealingGbn(blankToNull(raw.get("dealingGbn")))
                .rawJson(mapper.writeValueAsString(raw)).build();
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // 외부 엔티티를 막는다(XXE)
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw rtmsError("본문이 XML 이 아니다");
        }
    }

    private static String text(Element root, String tag) {
        NodeList n = root.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().trim();
    }

    private static UpstreamException rtmsError(String why) {
        return new UpstreamException(HttpStatus.BAD_GATEWAY, "RTMS_ERROR", "실거래 API " + why);
    }

    private static int intOf(String v) {
        Integer i = intOrNull(v);
        return i == null ? 0 : i;
    }

    private static Integer intOrNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOf(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal decimal(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }
}
