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
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 공고 주소 → 법정동코드·좌표(Kakao Local 주소 검색). 법정동코드 표를 저장소에 두지 않는다 — 2026년에 생긴 구·특례시처럼
 * 행정구역이 바뀌면 표가 낡는다. 주소 검색이 비면 단지명 키워드 검색으로 찾은 주소로 한 번 더 주소 검색을 한다
 * (키워드 결과에는 법정동코드가 없다).
 */
@Slf4j
@Service
public class KakaoGeoClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(10);

    @Value("${kakao.rest.key:}")
    private String kakaoKey;

    // 테스트가 가짜 서버로 갈아끼운다. 키워드 검색 주소는 채용 화면(JobService)과 같은 속성이다
    @Value("${kakao.address.url:https://dapi.kakao.com/v2/local/search/address.json}")
    private String addressUrl;

    @Value("${kakao.keyword.url:https://dapi.kakao.com/v2/local/search/keyword.json}")
    private String keywordUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final AtomicInteger calls = new AtomicInteger();

    /**
     * 공고 주소 문구 → 주소 검색어. 「(」「[」 뒤 설명과 쉼표 뒤 다른 동, 「일원」 뒤를 버리고 「번지」 를 뗀다.
     * 예) 경기도 화성특례시 동탄구 반송동 95번지[동탄(1) M1-1-2블록] → 경기도 화성특례시 동탄구 반송동 95
     */
    static String cleanAddress(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        for (String cut : new String[]{"(", "[", ",", "일원", " 외 "}) {
            int i = s.indexOf(cut);
            if (i > 0) {
                s = s.substring(0, i);
            }
        }
        return s.replace("번지", " ").replaceAll("\\s+", " ").trim();
    }

    public boolean enabled() {
        return kakaoKey != null && !kakaoKey.isBlank();
    }

    /** 주소 검색 → 비면 단지명 키워드 검색 → 그 주소로 다시 주소 검색. 못 찾으면 empty */
    public Optional<Geo> geocode(String address, String houseNm) {
        Optional<Geo> g = byAddress(cleanAddress(address));
        if (g.isPresent() || houseNm == null || houseNm.isBlank()) {
            return g;
        }
        JsonNode doc = get(keywordUrl, houseNm).path("documents").path(0);
        String found = doc.path("address_name").asString("");
        return found.isBlank() ? Optional.empty() : byAddress(found);
    }

    /** 이번 실행에서 부른 횟수 */
    public int callCount() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    private Optional<Geo> byAddress(String query) {
        if (query.isBlank()) {
            return Optional.empty();
        }
        for (JsonNode d : get(addressUrl, query).path("documents")) {
            JsonNode a = d.path("address");
            String bcode = a.path("b_code").asString("");
            if (bcode.length() == 10) {
                return Optional.of(new Geo(bcode.substring(0, 5), bcode, blankToNull(a.path("region_3depth_name").asString("")),
                        decimal(d.path("y").asString("")), decimal(d.path("x").asString("")), d.path("address_name").asString("")));
            }
        }
        return Optional.empty();
    }

    private JsonNode get(String url, String query) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)))
                .timeout(READ_TIMEOUT).header("Authorization", "KakaoAK " + kakaoKey).GET().build();
        calls.incrementAndGet();
        HttpResponse<String> res;
        try {
            res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("카카오 주소 검색 연결 실패 - {}", e.getClass().getSimpleName());
            throw kakaoError("연결 실패");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw kakaoError("중단");
        }
        if (res.statusCode() != 200) {
            throw kakaoError("HTTP " + res.statusCode());
        }
        try {
            return mapper.readTree(res.body());
        } catch (JacksonException e) {
            throw kakaoError("본문이 JSON 이 아니다");
        }
    }

    private static UpstreamException kakaoError(String why) {
        return new UpstreamException(HttpStatus.BAD_GATEWAY, "KAKAO_ERROR", "카카오 주소 검색 " + why);
    }

    private static BigDecimal decimal(String v) {
        try {
            return v.isBlank() ? null : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String v) {
        return v.isBlank() ? null : v;
    }
}
