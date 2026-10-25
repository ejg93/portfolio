package com.portfolio.cheongyak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CollectController.class)
@TestPropertySource(properties = {"cors.allowed-origins=http://localhost:3000", "cheongyak.collect.token=abc"})
class CollectControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CollectService service;

    @Test
    @DisplayName("토큰 헤더가 없으면 401")
    void noHeader() throws Exception {
        mvc.perform(post("/api/cheongyak/collect")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("토큰이 다르면 401")
    void wrongToken() throws Exception {
        mvc.perform(post("/api/cheongyak/collect").header("X-Collect-Token", "abd")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("맞으면 202 와 RUNNING 기록")
    void accepted() throws Exception {
        when(service.start("cron")).thenReturn(CollectRun.builder().id(7L).status(CollectRun.RUNNING)
                .triggerSrc("cron").sinceDate(LocalDate.of(2025, 10, 25)).build());
        mvc.perform(post("/api/cheongyak/collect?src=cron").header("X-Collect-Token", "abc"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("이미 돌고 있으면 409")
    void busy() throws Exception {
        when(service.start("manual")).thenThrow(new CollectBusyException());
        mvc.perform(post("/api/cheongyak/collect").header("X-Collect-Token", "abc"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECT_RUNNING"));
    }
}
