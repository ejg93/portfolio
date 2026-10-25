package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 한국부동산원 청약홈 분양정보 조회 서비스(odcloud). 공고 상세는 페이지를 넘기며 다 받고, 주택형은 공고마다 한 번 부른다.
 * 같은 키로 경쟁률 서비스(ApplyhomeInfoCmpetRtSvc)의 경쟁률·당첨가점도 공고마다 한 번 부른다.
 * 키는 Decoding 키를 받아 한 번만 URL 인코딩한다 — Encoding 키를 넣으면 이중 인코딩으로 인증이 깨진다.
 */
@Slf4j
@Service
public class ApplyhomeClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(30);
    private static final int PER_PAGE  = 100;
    // 수도권 1년치가 종류당 수백 건이다. 60장(6,000건)을 넘으면 필터가 안 먹은 것으로 본다
    private static final int MAX_PAGES = 60;

    @Value("${data.go.kr.key:}")
    private String serviceKey;

    // 테스트가 가짜 서버로 갈아끼운다
    @Value("${cheongyak.applyhome.base-url:https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1}")
    private String baseUrl;

    @Value("${cheongyak.cmpet.base-url:https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1}")
    private String cmpetBaseUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final AtomicInteger calls = new AtomicInteger();

    /** 공고일이 since 이후인 공고 상세 전부 */
    public List<JsonNode> fetchDetails(ApplyhomeKind kind, LocalDate since) {
        Map<String, String> cond = Map.of("cond[RCRIT_PBLANC_DE::GTE]", since.format(kind.dateFormat()));
        List<JsonNode> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = call(baseUrl, kind.detailOp(), cond, page);
            JsonNode data = body.path("data");
            data.forEach(out::add);
            if (body.path("currentCount").asInt(data.size()) < PER_PAGE) {
                return out;
            }
        }
        throw new UpstreamException(HttpStatus.BAD_GATEWAY, "PAGE_CAP", "청약홈 API 페이지가 " + MAX_PAGES + "장을 넘었다");
    }

    /** 공고 하나의 주택형. 한 공고에 100형을 넘는 경우는 없다 */
    public List<JsonNode> fetchHouseTypes(ApplyhomeKind kind, String pblancNo) {
        List<JsonNode> out = new ArrayList<>();
        call(baseUrl, kind.modelOp(), Map.of("cond[PBLANC_NO::EQ]", pblancNo), 1).path("data").forEach(out::add);
        return out;
    }

    /** 공고 하나의 경쟁률. APT·무순위만. 한 공고의 줄(주택형 × 순위 × 거주)이 100을 넘는 경우는 없다 */
    public List<JsonNode> fetchCmpet(ApplyhomeKind kind, String hmn, String pno) {
        String op = switch (kind) {
            case APT -> "getAPTLttotPblancCmpet";
            case REMNDR -> "getRemndrLttotPblancCmpet";
            default -> throw new IllegalArgumentException("경쟁률은 APT·무순위만 받는다: " + kind);
        };
        return rows(op, hmn, pno);
    }

    /** APT 공고 하나의 당첨가점 */
    public List<JsonNode> fetchScores(String hmn, String pno) {
        return rows("getAptLttotPblancScore", hmn, pno);
    }

    /** 이번 실행에서 부른 횟수. 수집 이력에 남긴다 */
    public int callCount() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    private List<JsonNode> rows(String op, String hmn, String pno) {
        Map<String, String> cond = new LinkedHashMap<>();
        cond.put("cond[HOUSE_MANAGE_NO::EQ]", hmn);
        cond.put("cond[PBLANC_NO::EQ]", pno);
        List<JsonNode> out = new ArrayList<>();
        call(cmpetBaseUrl, op, cond, 1).path("data").forEach(out::add);
        return out;
    }

    private JsonNode call(String base, String op, Map<String, String> cond, int page) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "KEY_NOT_SET", "DATA_GO_KR_KEY 가 없다");
        }
        Map<String, String> q = new LinkedHashMap<>();
        q.put("page", String.valueOf(page));
        q.put("perPage", String.valueOf(PER_PAGE));
        q.putAll(cond);
        q.put("serviceKey", serviceKey);
        String query = q.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/" + op + "?" + query))
                .timeout(READ_TIMEOUT).header("Accept", "application/json").GET().build();
        calls.incrementAndGet();
        HttpResponse<String> res;
        try {
            res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("청약홈 API {} 연결 실패 - {}", op, e.getClass().getSimpleName());
            throw upstream();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw upstream();
        }
        if (res.statusCode() != 200) {
            log.warn("청약홈 API {} - HTTP {}", op, res.statusCode());
            throw upstream();
        }
        try {
            return mapper.readTree(res.body());
        } catch (JacksonException e) {
            // 점검 안내 HTML 이 200 으로 오는 경우다
            log.warn("청약홈 API {} 200 인데 본문이 JSON 이 아니다 - {}자", op, res.body().length());
            throw upstream();
        }
    }

    private static UpstreamException upstream() {
        return new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "청약홈 API 응답이 정상이 아니다");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
