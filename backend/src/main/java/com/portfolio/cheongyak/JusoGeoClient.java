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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고 주소 → 법정동코드(행정안전부 도로명주소 검색 API). 법정동코드 표를 저장소에 두지 않는다 — 2026년에 생긴 구·특례시처럼
 * 행정구역이 바뀌면 표가 낡는다. 좌표는 이 API 가 주지 않아 lat·lng 은 비운다.
 * 이 API 는 지금 있는 건물만 찾는다. 분양 단지는 아직 건물이 없는 번지가 많아서 번지로 안 맞으면 읍면동까지로 줄여 다시 찾는다.
 */
@Slf4j
@Service
public class JusoGeoClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(10);
    private static final Pattern BRACKET = Pattern.compile("[(\\[]([^)\\]]*)[)\\]]");
    // 이 API 가 검색어로 안 받는 문자. 섞이면 결과 대신 오류 코드가 온다
    private static final Pattern FORBIDDEN = Pattern.compile("[%=><]");
    // 승인키 오류·기간 만료·시스템 오류. 이 셋 말고는 검색어 문제라 다음 후보로 넘어간다
    private static final Set<String> FATAL = Set.of("E0001", "E0014", "-999");

    @Value("${juso.key:}")
    private String jusoKey;

    // 테스트가 가짜 서버로 갈아끼운다
    @Value("${juso.url:https://business.juso.go.kr/addrlink/addrLinkApi.do}")
    private String jusoUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final AtomicInteger calls = new AtomicInteger();

    /**
     * 공고 주소 문구 → 주소 검색어. 「(」「[」 뒤 설명과 쉼표 뒤 다른 동, 「일원」「일대」「외 n필지」 뒤를 버리고 「번지」 를 뗀다.
     * 예) 경기도 화성특례시 동탄구 반송동 95번지[동탄(1) M1-1-2블록] → 경기도 화성특례시 동탄구 반송동 95
     */
    static String cleanAddress(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw;
        for (String cut : new String[]{"(", "[", ",", "일원", "일대", "외 "}) {
            int i = s.indexOf(cut);
            if (i > 0) {
                s = s.substring(0, i);
            }
        }
        return FORBIDDEN.matcher(s.replace("번지", " ")).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    /** 검색어를 읍면동까지로 줄인다. 읍면동 낱말이 없으면 null. 예) 서울특별시 관악구 신림동 505-1 → 서울특별시 관악구 신림동 */
    static String upToDong(String query) {
        String[] t = query.split(" ");
        for (int i = 1; i < t.length; i++) {
            if (t[i].matches(".+(동|읍|면|가)") && !t[i].matches(".+(구|시|군)")) {
                return String.join(" ", List.of(t).subList(0, i + 1));
            }
        }
        return null;
    }

    /** 찾아볼 순서: 괄호 밖 주소 → 그 읍면동 → 괄호 안 주소 → 그 읍면동. 괄호 안에 진짜 지번이 든 공고가 있다 */
    static List<String> candidates(String raw) {
        List<String> base = new ArrayList<>();
        base.add(cleanAddress(raw));
        if (raw != null) {
            Matcher m = BRACKET.matcher(raw);
            while (m.find()) {
                base.add(cleanAddress(m.group(1)));
            }
        }
        Set<String> out = new LinkedHashSet<>();
        for (String q : base) {
            if (!q.isBlank()) {
                out.add(q);
                String dong = upToDong(q);
                if (dong != null) {
                    out.add(dong);
                }
            }
        }
        return List.copyOf(out);
    }

    public boolean enabled() {
        return jusoKey != null && !jusoKey.isBlank();
    }

    /** 후보 검색어를 차례로 불러 처음 맞은 주소의 법정동코드. 다 비면 empty */
    public Optional<Geo> geocode(String address) {
        for (String q : candidates(address)) {
            Optional<Geo> g = search(q);
            if (g.isPresent()) {
                return g;
            }
        }
        return Optional.empty();
    }

    /** 이번 실행에서 부른 횟수 */
    public int callCount() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }

    private Optional<Geo> search(String query) {
        JsonNode results = get(query).path("results");
        String code = results.path("common").path("errorCode").asString("");
        if (!"0".equals(code)) {
            if (FATAL.contains(code)) {
                throw jusoError(code + " " + results.path("common").path("errorMessage").asString(""));
            }
            return Optional.empty();
        }
        for (JsonNode j : results.path("juso")) {
            String admCd = j.path("admCd").asString("");
            if (admCd.length() == 10) {
                return Optional.of(new Geo(admCd.substring(0, 5), admCd, blankToNull(j.path("emdNm").asString("")),
                        null, null, j.path("jibunAddr").asString("")));
            }
        }
        return Optional.empty();
    }

    private JsonNode get(String query) {
        String qs = "confmKey=" + enc(jusoKey) + "&keyword=" + enc(query) + "&resultType=json&currentPage=1&countPerPage=1";
        HttpRequest req = HttpRequest.newBuilder(URI.create(jusoUrl + "?" + qs)).timeout(READ_TIMEOUT).GET().build();
        calls.incrementAndGet();
        HttpResponse<String> res;
        try {
            res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("도로명주소 검색 연결 실패 - {}", e.getClass().getSimpleName());
            throw jusoError("연결 실패");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw jusoError("중단");
        }
        if (res.statusCode() != 200) {
            throw jusoError("HTTP " + res.statusCode());
        }
        try {
            return mapper.readTree(res.body());
        } catch (JacksonException e) {
            throw jusoError("본문이 JSON 이 아니다");
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static UpstreamException jusoError(String why) {
        return new UpstreamException(HttpStatus.BAD_GATEWAY, "JUSO_ERROR", "도로명주소 검색 " + why);
    }

    private static String blankToNull(String v) {
        return v.isBlank() ? null : v;
    }
}
