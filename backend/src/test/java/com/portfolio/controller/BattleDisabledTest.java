package com.portfolio.controller;

import com.portfolio.service.BattleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 속성을 아예 안 주고 기본값(꺼짐)을 확인한다. 배포 환경변수를 빠뜨렸을 때 열려 버리면 안 된다
@WebMvcTest(BattleController.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class BattleDisabledTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BattleService battleService;

    @Test
    @DisplayName("기본값은 꺼짐이라 503 이고 모델 API 를 안 부른다")
    void disabledByDefault() throws Exception {
        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"persona\":\"logic\",\"userMsg\":\"한 마디\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("BATTLE_DISABLED"));

        verify(battleService, never()).chat(any());
    }
}
