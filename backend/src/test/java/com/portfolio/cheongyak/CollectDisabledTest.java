package com.portfolio.cheongyak;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 토큰을 안 넣은 배포(로컬 등)에서는 수집 입구 자체를 닫는다
@WebMvcTest(CollectController.class)
@TestPropertySource(properties = {"cors.allowed-origins=http://localhost:3000", "cheongyak.collect.token="})
class CollectDisabledTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CollectService service;

    @Test
    @DisplayName("수집 토큰이 비면 503 COLLECT_DISABLED")
    void disabled() throws Exception {
        mvc.perform(post("/api/cheongyak/collect").header("X-Collect-Token", ""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COLLECT_DISABLED"));
    }
}
