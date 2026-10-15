package com.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import com.portfolio.service.JobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobService jobService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("구인 목록은 jobs 배열을 돌려준다")
    void jobs() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        ObjectNode job = mapper.createObjectNode();
        job.put("company", "어떤회사");
        job.put("repeatCount", 3);
        body.set("jobs", mapper.createArrayNode().add(job));

        given(jobService.fetchJobs()).willReturn(body);

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].company").value("어떤회사"))
                .andExpect(jsonPath("$.jobs[0].repeatCount").value(3));
    }

    @Test
    @DisplayName("사람인 키가 없으면 200 에 error 필드가 실려 나간다")
    void jobsWithoutKey() throws Exception {
        // 키가 비면 서비스가 예외 대신 error 필드를 담은 200 을 만든다. 화면은 빈 표를 그린다
        ObjectNode body = mapper.createObjectNode();
        body.put("error", "SARAMIN_ACCESS_KEY not set");
        body.set("jobs", mapper.createArrayNode());

        given(jobService.fetchJobs()).willReturn(body);

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("SARAMIN_ACCESS_KEY not set"))
                .andExpect(jsonPath("$.jobs").isEmpty());
    }

    @Test
    @DisplayName("업스트림이 터지면 502 에 UPSTREAM_ERROR 로 나간다")
    void jobsFailure() throws Exception {
        given(jobService.fetchJobs()).willThrow(
                new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "채용정보 API 응답이 정상이 아니다"));

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    @Test
    @DisplayName("예상 못 한 예외도 502 고 예외 메시지는 안 실린다")
    void jobsUnexpectedFailure() throws Exception {
        given(jobService.fetchJobs()).willThrow(new IllegalStateException("access-key 가 든 내부 메시지"));

        mvc.perform(get("/api/jobs"))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("access-key"))));
    }
}
