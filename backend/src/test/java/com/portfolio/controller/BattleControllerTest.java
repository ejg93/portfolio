package com.portfolio.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.dto.BattleRequest;
import com.portfolio.dto.BattleResponse;
import com.portfolio.error.UpstreamException;
import com.portfolio.service.BattleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 계층만 띄운다. 외부 API 를 부르는 서비스는 가짜로 바꿔서 키 없이도 돈다.
// WebConfig 가 WebMvcConfigurer 라 이 슬라이스에도 딸려 온다 — 그래서 CORS 속성이 필요하다.
// 여기서는 켜 둔 상태를 본다. 꺼진 상태는 BattleDisabledTest 가 맡는다.
@WebMvcTest(BattleController.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:3000",
        "battle.enabled=true",
        "battle.max-user-msg=2000",
        "battle.max-history=10",
})
class BattleControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private BattleService battleService;

    private String body(String persona, String userMsg) throws Exception {
        BattleRequest req = new BattleRequest();
        req.setPersona(persona);
        req.setUserMsg(userMsg);
        return mapper.writeValueAsString(req);
    }

    @Test
    @DisplayName("채팅 요청은 reply 와 tokens 를 돌려준다")
    void chat() throws Exception {
        given(battleService.chat(any(BattleRequest.class)))
                .willReturn(new BattleResponse("그건 근거가 없다", 42));

        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logic", "고양이가 최고다")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("그건 근거가 없다"))
                .andExpect(jsonPath("$.tokens").value(42));
    }

    @Test
    @DisplayName("모델 API 가 429 면 429 에 QUOTA_EXCEEDED 로 나간다")
    void chatQuota() throws Exception {
        given(battleService.chat(any(BattleRequest.class)))
                .willThrow(new UpstreamException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED", "모델 API 응답이 정상이 아니다"));

        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logic", "한 마디")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("서비스가 터지면 502 로 나가고 업스트림 원문은 안 실린다")
    void chatFailure() throws Exception {
        given(battleService.chat(any(BattleRequest.class)))
                .willThrow(new RuntimeException("x-api-key 어쩌고 하는 내부 사정"));

        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logic", "한 마디")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("x-api-key"))));
    }

    @Test
    @DisplayName("본문이 JSON 이 아니면 400 이다")
    void chatBrokenBody() throws Exception {
        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("userMsg 가 비면 400 INVALID_REQUEST 다")
    void chatEmptyMessage() throws Exception {
        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logic", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("userMsg 가 상한을 넘으면 400 MESSAGE_TOO_LONG 이다")
    void chatTooLong() throws Exception {
        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("logic", "가".repeat(2001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MESSAGE_TOO_LONG"));
    }

    @Test
    @DisplayName("history 가 상한을 넘으면 400 HISTORY_TOO_LONG 이다")
    void chatHistoryTooLong() throws Exception {
        BattleRequest req = new BattleRequest();
        req.setPersona("logic");
        req.setUserMsg("한 마디");
        List<BattleRequest.Message> history = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            BattleRequest.Message m = new BattleRequest.Message();
            m.setRole("user");
            m.setContent("이전 대화");
            history.add(m);
        }
        req.setHistory(history);

        mvc.perform(post("/api/battle/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HISTORY_TOO_LONG"));
    }
}
