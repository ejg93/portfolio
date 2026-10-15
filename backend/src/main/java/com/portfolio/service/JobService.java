package com.portfolio.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class JobService {

    @Value("${saramin.access.key:}")
    private String saraminKey;

    @Value("${kakao.rest.key:}")
    private String kakaoKey;

    // 테스트가 가짜 서버로 갈아끼운다. 평소에는 이 기본값을 쓴다
    @Value("${saramin.api.url:https://oapi.saramin.co.kr/job-search}")
    private String saraminUrl;

    @Value("${kakao.keyword.url:https://dapi.kakao.com/v2/local/search/keyword.json}")
    private String kakaoKeywordUrl;

    // 서울시 중랑구 동일로 143길 70-10
    private static final double USER_LAT = 37.5966;
    private static final double USER_LNG = 127.0869;




    private final ObjectMapper mapper     = new ObjectMapper();
    // 타임아웃이 없으면 업스트림이 안 끊을 때 톰캣 스레드가 그대로 물린다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(20);

    private final HttpClient   httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public ObjectNode fetchJobs() throws Exception {
        if (saraminKey == null || saraminKey.isBlank()) {
            ObjectNode err = mapper.createObjectNode();
            err.put("error", "SARAMIN_ACCESS_KEY not set");
            err.set("jobs", mapper.createArrayNode());
            return err;
        }

        String url = saraminUrl
                + "?access-key=" + saraminKey
                + "&job_mid_cd=2&loc_mcd=101000&count=30&sort=pd";

        HttpRequest req = HttpRequest.newBuilder()
                .timeout(READ_TIMEOUT)
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.warn("Saramin API {} - {}", res.statusCode(), res.body());
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "채용정보 API 응답이 정상이 아니다");
        }

        JsonNode data;
        try {
            data = mapper.readTree(res.body());
        } catch (JacksonException e) {
            // 점검 안내 HTML 이 200 으로 오는 경우다. 200 이 아닐 때와 같은 자리로 모은다
            log.warn("Saramin API 200 인데 본문이 JSON 이 아니다 - {}자", res.body().length());
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "채용정보 API 응답이 정상이 아니다");
        }
        JsonNode rawJobs = data.path("jobs").path("job");

        // 1차: 모든 공고 수집
        List<ObjectNode> jobList = new ArrayList<>();
        for (JsonNode raw : rawJobs) {
            jobList.add(buildJob(raw));
        }

        // 반복공고 감지: 동일 회사 공고 수 카운트
        Map<String, Integer> companyCount = new HashMap<>();
        for (ObjectNode job : jobList) {
            String co = job.path("company").asString("");
            companyCount.merge(co, 1, Integer::sum);
        }
        for (ObjectNode job : jobList) {
            job.put("repeatCount", companyCount.getOrDefault(job.path("company").asString(""), 1));
        }

        ArrayNode jobs = mapper.createArrayNode();
        jobList.forEach(jobs::add);

        ObjectNode out = mapper.createObjectNode();
        out.set("jobs", jobs);
        return out;
    }

    private ObjectNode buildJob(JsonNode raw) {
        String locationRaw = raw.path("position").path("location").path("name").asString("");
        String location    = locationRaw.replace("&gt;", " ").replaceAll("\\s+", " ").trim();

        String salaryName = raw.path("salary").path("name").asString("협의");
        int    salaryMin  = parseSalaryMin(salaryName);
        int    expMin     = raw.path("position").path("experience-level").path("min").asInt(0);

        ObjectNode job = mapper.createObjectNode();
        job.put("id",        raw.path("id").asString(""));
        job.put("url",       raw.path("url").asString(""));
        job.put("company",   raw.path("company").path("detail").path("name").asString(""));
        job.put("title",     raw.path("position").path("title").asString(""));
        job.put("location",  location);
        job.put("salary",    salaryName);
        job.put("salaryMin", salaryMin);
        job.put("expMin",    expMin);
        job.put("expName",   raw.path("position").path("experience-level").path("name").asString("경력무관"));
        job.put("education", raw.path("position").path("required-education-level").path("name").asString(""));
        job.put("jobType",   raw.path("position").path("job-type").path("name").asString(""));

        if (!location.isBlank()) {
            try {
                double[] coords = geocode(location);
                if (coords != null) {
                    job.put("lat",      coords[0]);
                    job.put("lng",      coords[1]);
                    double dist = haversine(USER_LAT, USER_LNG, coords[0], coords[1]);
                    job.put("distance", Math.round(dist * 10.0) / 10.0);
                }
            } catch (Exception e) {
                log.warn("지오코딩 실패: {} - {}", location, e.getMessage());
            }
        }

        if (!job.has("lat")) {
            job.putNull("lat");
            job.putNull("lng");
            job.putNull("distance");
        }

        return job;
    }

    private double[] geocode(String query) throws Exception {
        String url = kakaoKeywordUrl
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&size=1";
        HttpRequest req = HttpRequest.newBuilder()
                .timeout(READ_TIMEOUT)
                .uri(URI.create(url))
                .header("Authorization", "KakaoAK " + kakaoKey)
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonNode docs = mapper.readTree(res.body()).path("documents");
        if (docs.isEmpty()) return null;
        return new double[]{ docs.get(0).path("y").asDouble(), docs.get(0).path("x").asDouble() };
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R    = 6371;
        double       dLat = Math.toRadians(lat2 - lat1);
        double       dLng = Math.toRadians(lng2 - lng1);
        double       a    = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.asin(Math.sqrt(a));
    }

    private int parseSalaryMin(String name) {
        StringBuilder sb       = new StringBuilder();
        boolean       inNumber = false;
        for (char c : name.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
                inNumber = true;
            } else if (c == ',' && inNumber) {
                // 천단위 구분자 스킵
            } else if (inNumber) {
                break;
            }
        }
        if (sb.isEmpty()) return 0;
        try { return Integer.parseInt(sb.toString()); }
        catch (NumberFormatException e) { return 0; }
    }
}
