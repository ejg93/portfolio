package com.portfolio.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 사람인·카카오를 가짜 서버로 바꿔 놓고, 받은 JSON 을 화면이 쓰는 모양으로 어떻게 바꾸는지 본다.
// 급여 문자열 파싱, 지역명 정리, 반복공고 세기, 거리 계산이 여기 들어 있다.
class JobServiceTest {

    private StubServer stub;
    private JobService service;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        service = new JobService();
        ReflectionTestUtils.setField(service, "saraminKey", "test-key");
        ReflectionTestUtils.setField(service, "kakaoKey", "test-kakao");
        ReflectionTestUtils.setField(service, "saraminUrl", stub.url() + "/job-search");
        ReflectionTestUtils.setField(service, "kakaoKeywordUrl", stub.url() + "/keyword.json");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private void saramin(String jobsJson) {
        stub.on("/job-search", q -> new Object[]{200, "{\"jobs\":{\"job\":[" + jobsJson + "]}}"});
    }

    private static String job(String id, String company, String location, String salary, int expMin) {
        return """
                {"id":"%s","url":"https://example.test/%s",
                 "company":{"detail":{"name":"%s"}},
                 "position":{"title":"백엔드 개발자","location":{"name":"%s"},
                   "experience-level":{"min":%d,"name":"경력 %d년"},
                   "required-education-level":{"name":"대졸"},"job-type":{"name":"정규직"}},
                 "salary":{"name":"%s"}}""".formatted(id, id, company, location, expMin, expMin, salary);
    }

    /** 지오코딩은 안 되는 것으로 둔다. 거리 없이도 목록이 나와야 한다 */
    private void kakaoEmpty() {
        stub.on("/keyword.json", q -> new Object[]{200, "{\"documents\":[]}"});
    }

    @Test
    @DisplayName("사람인 키가 없으면 부르지도 않고 error 필드를 돌려준다")
    void noKey() throws Exception {
        ReflectionTestUtils.setField(service, "saraminKey", "");

        ObjectNode out = service.fetchJobs();

        assertThat(out.path("error").asString()).isEqualTo("SARAMIN_ACCESS_KEY not set");
        assertThat(out.path("jobs")).isEmpty();
        // 키가 없는데 요청을 보내면 그쪽 로그만 더럽힌다
        assertThat(stub.requests()).isEmpty();
    }

    @Test
    @DisplayName("급여 문자열에서 최소 금액만 숫자로 뽑는다")
    void parsesSalary() throws Exception {
        kakaoEmpty();
        saramin(String.join(",",
                job("1", "가회사", "서울", "2,400만원 이상", 0),
                job("2", "나회사", "서울", "면접 후 결정", 0),
                job("3", "다회사", "서울", "3500~4000만원", 0)));

        JsonNode jobs = service.fetchJobs().path("jobs");

        assertThat(jobs.get(0).path("salaryMin").asInt()).isEqualTo(2400);
        // 숫자가 없으면 0 이다. 정렬에서 맨 뒤로 가라는 뜻이다
        assertThat(jobs.get(1).path("salaryMin").asInt()).isZero();
        assertThat(jobs.get(2).path("salaryMin").asInt()).isEqualTo(3500);
    }

    @Test
    @DisplayName("지역명의 &gt; 와 겹친 공백을 정리한다")
    void cleansLocation() throws Exception {
        kakaoEmpty();
        saramin(job("1", "가회사", "서울 &gt;  강남구", "협의", 0));

        JsonNode job = service.fetchJobs().path("jobs").get(0);

        assertThat(job.path("location").asString()).isEqualTo("서울 강남구");
    }

    @Test
    @DisplayName("같은 회사가 여러 건 올리면 그 수를 세어 붙인다")
    void countsRepeats() throws Exception {
        kakaoEmpty();
        saramin(String.join(",",
                job("1", "반복회사", "서울", "협의", 0),
                job("2", "반복회사", "서울", "협의", 1),
                job("3", "한번회사", "서울", "협의", 0)));

        JsonNode jobs = service.fetchJobs().path("jobs");

        assertThat(jobs.get(0).path("repeatCount").asInt()).isEqualTo(2);
        assertThat(jobs.get(1).path("repeatCount").asInt()).isEqualTo(2);
        assertThat(jobs.get(2).path("repeatCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("좌표를 받으면 기준 위치에서 몇 km 인지 소수 한 자리로 붙인다")
    void computesDistance() throws Exception {
        // 기준 위치는 서울시 중랑구(37.5966, 127.0869). 위도만 0.1 도 올린 지점을 준다 —
        // 위도 1 도는 약 111km 라 11.1km 근처가 나와야 한다
        stub.on("/keyword.json", q -> new Object[]{200, """
                {"documents":[{"y":"37.6966","x":"127.0869"}]}"""});
        saramin(job("1", "가회사", "서울 노원구", "협의", 0));

        JsonNode job = service.fetchJobs().path("jobs").get(0);

        assertThat(job.path("lat").asDouble()).isEqualTo(37.6966);
        assertThat(job.path("distance").asDouble()).isBetween(11.0, 11.2);
        // 지오코딩에 지역명을 인코딩해 넘겼는지
        assertThat(stub.requests()).anyMatch(r -> r.startsWith("/keyword.json") && r.contains("query="));
    }

    @Test
    @DisplayName("좌표를 못 찾으면 거리 칸을 비워 둔다")
    void nullDistanceWhenGeocodeFails() throws Exception {
        kakaoEmpty();
        saramin(job("1", "가회사", "어딘가", "협의", 0));

        JsonNode job = service.fetchJobs().path("jobs").get(0);

        // 0 으로 채우면 「제일 가까움」으로 정렬돼 거짓말이 된다
        assertThat(job.path("lat").isNull()).isTrue();
        assertThat(job.path("distance").isNull()).isTrue();
    }

    @Test
    @DisplayName("지오코딩이 통째로 실패해도 목록은 나온다")
    void geocodeFailureIsSwallowed() throws Exception {
        stub.on("/keyword.json", q -> new Object[]{401, "{\"msg\":\"bad key\"}"});
        saramin(job("1", "가회사", "서울", "협의", 0));

        JsonNode jobs = service.fetchJobs().path("jobs");

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).path("distance").isNull()).isTrue();
    }

    @Test
    @DisplayName("사람인이 200 인데 본문이 JSON 이 아니면 502 UPSTREAM_ERROR 로 바꾼다")
    void brokenSuccessBody() {
        // 점검 안내 페이지가 200 으로 오는 경우. 파서 예외를 그대로 올리면 502 가 아니라 500 처럼 보인다
        stub.on("/job-search", q -> new Object[]{200, "<html>점검 중</html>"});

        assertThatThrownBy(() -> service.fetchJobs())
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                    assertThat(e.getMessage()).doesNotContain("점검 중");
                });
    }

    @Test
    @DisplayName("공고가 한 건도 없으면 빈 배열을 돌려준다")
    void emptyResult() throws Exception {
        stub.on("/job-search", q -> new Object[]{200, "{\"jobs\":{\"count\":0}}"});

        ObjectNode out = service.fetchJobs();

        assertThat(out.path("jobs")).isEmpty();
        assertThat(out.has("error")).isFalse();
    }

    @Test
    @DisplayName("지오코딩 응답이 JSON 이 아니어도 목록은 나오고 거리 칸만 빈다")
    void brokenGeocodeBody() throws Exception {
        stub.on("/keyword.json", q -> new Object[]{200, "not json"});
        saramin(job("1", "가회사", "서울", "협의", 0));

        JsonNode jobs = service.fetchJobs().path("jobs");

        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).path("lat").isNull()).isTrue();
        assertThat(jobs.get(0).path("distance").isNull()).isTrue();
    }

    @Test
    @DisplayName("사람인이 200 이 아니면 502 UPSTREAM_ERROR 로 바꾼다")
    void upstreamFailure() {
        stub.on("/job-search", q -> new Object[]{500, "{\"msg\":\"서버 사정\"}"});

        assertThatThrownBy(() -> service.fetchJobs())
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                    assertThat(e.getMessage()).doesNotContain("서버 사정");
                });
    }
}
