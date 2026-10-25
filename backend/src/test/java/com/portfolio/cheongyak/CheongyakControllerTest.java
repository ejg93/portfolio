package com.portfolio.cheongyak;

import com.portfolio.cheongyak.dto.JudgeRequest;
import com.portfolio.cheongyak.dto.JudgeResponse;
import com.portfolio.cheongyak.rules.Applicant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheongyakController.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class CheongyakControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheongyakQueryService service;

    @Test
    @DisplayName("공고 목록 — 기본은 수도권, kind 는 쉼표로 나눠 넘긴다")
    void notices() throws Exception {
        when(service.notices(isNull(), isNull(), eq("capital"), eq(Set.of("APT", "REMNDR")))).thenReturn(List.of());
        mvc.perform(get("/api/cheongyak/notices?kind=APT,REMNDR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("지역 값이 낯설면 400")
    void noticesBadArea() throws Exception {
        mvc.perform(get("/api/cheongyak/notices?area=부산광역시")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("판정 — 생년월일이 없으면 400")
    void judgeWithoutBirth() throws Exception {
        mvc.perform(post("/api/cheongyak/judge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicant\":{\"residenceSido\":\"서울\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("판정 — 경기 거주자의 시군이 목록 밖(「서울」)이면 400")
    void judgeBadCity() throws Exception {
        mvc.perform(post("/api/cheongyak/judge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicant\":" + fullApplicant("경기", "서울") + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("판정 — 신청자 칸에 빈 칸이 있으면 400 INVALID_REQUEST")
    void judgeMissingField() throws Exception {
        mvc.perform(post("/api/cheongyak/judge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicant\":{\"residenceSido\":\"서울\",\"birthDate\":\"1990-01-01\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static String fullApplicant(String sido, String city) {
        return "{\"residenceSido\":\"" + sido + "\",\"birthDate\":\"1990-01-01\",\"marriageDate\":null,\"householdHomeless\":true,"
                + "\"homelessSince\":null,\"householdHouseCount\":0,\"householder\":true,\"dependents\":1,\"accountMonths\":60,"
                + "\"paymentCount\":60,\"depositManwon\":300,\"savingsTotalManwon\":0,\"spouseAccountMonths\":0,\"wonWithin5y\":false,"
                + "\"wonByPointsWithin2y\":false,\"residenceMonthsInSido\":24,\"residenceCity\":" + (city == null ? "null" : "\"" + city + "\"")
                + ",\"residenceMonthsInCity\":0}";
    }

    @Test
    @DisplayName("판정 — 서울 거주자가 보낸 시군은 버리고 넘긴다")
    void judgeOk() throws Exception {
        when(service.judge(any())).thenReturn(new JudgeResponse(LocalDateTime.of(2026, 10, 25, 9, 0), List.of()));
        mvc.perform(post("/api/cheongyak/judge").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicant\":" + fullApplicant("서울", "평택") + ","
                                + "\"notices\":[{\"houseManageNo\":\"1\",\"pblancNo\":\"1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
        ArgumentCaptor<JudgeRequest> sent = ArgumentCaptor.forClass(JudgeRequest.class);
        verify(service).judge(sent.capture());
        Applicant a = sent.getValue().applicant();
        assertThat(a.residenceCity()).isNull();
        assertThat(a.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(a.accountMonths()).isEqualTo(60);
    }

    @Test
    @DisplayName("수집 이력 목록")
    void runs() throws Exception {
        when(service.runs(anyInt())).thenReturn(List.of(CollectRun.builder().id(3L).status("OK").build()));
        mvc.perform(get("/api/cheongyak/runs")).andExpect(status().isOk()).andExpect(jsonPath("$.runs[0].id").value(3));
    }

    @Test
    @DisplayName("수집 이력 한 건 — 있으면 200, 없으면 404")
    void runById() throws Exception {
        when(service.run(5L)).thenReturn(Optional.of(CollectRun.builder().id(5L).status("RUNNING").build()));
        mvc.perform(get("/api/cheongyak/runs/5")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        when(service.run(6L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/cheongyak/runs/6")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("규칙 표")
    void rules() throws Exception {
        when(service.rules()).thenReturn(Map.of("deposit", Map.of("table", new int[][]{{300}}), "ggCities", List.of("평택")));
        mvc.perform(get("/api/cheongyak/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deposit").exists()).andExpect(jsonPath("$.ggCities[0]").value("평택"));
    }

    @Test
    @DisplayName("저장소가 안 붙으면 503")
    void dbDown() throws Exception {
        when(service.runs(anyInt())).thenThrow(new DataAccessResourceFailureException("down"));
        mvc.perform(get("/api/cheongyak/runs")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DB_UNAVAILABLE"));
    }
}
