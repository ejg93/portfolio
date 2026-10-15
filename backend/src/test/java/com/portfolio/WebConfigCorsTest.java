package com.portfolio;

import com.portfolio.controller.BattleController;
import com.portfolio.service.BattleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CORS 는 브라우저에서만 드러난다. 설정이 틀리면 로컬 프론트가 통째로 못 부르는데
// 백엔드 로그에는 아무것도 안 남아서 원인 찾는 데 오래 걸린다. 여기서 프리플라이트로 확인한다.
@WebMvcTest(BattleController.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000, http://localhost:3100")
class WebConfigCorsTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BattleService battleService;

    @Test
    @DisplayName("허용한 오리진의 프리플라이트는 통과한다")
    void preflightAllowed() throws Exception {
        mvc.perform(options("/api/battle/chat")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("쉼표로 이어 적은 둘째 오리진도 통과한다")
    void preflightSecondOrigin() throws Exception {
        // 속성값을 통째로 넘기면 「a, b」 라는 오리진 하나가 되어 둘 다 막힌다. 그 회귀를 여기서 잡는다
        mvc.perform(options("/api/battle/chat")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3100")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3100"));
    }

    @Test
    @DisplayName("목록에 없는 오리진은 막힌다")
    void preflightBlocked() throws Exception {
        mvc.perform(options("/api/battle/chat")
                        .header(HttpHeaders.ORIGIN, "https://not-mine.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
