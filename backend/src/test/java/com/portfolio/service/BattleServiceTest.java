package com.portfolio.service;

import tools.jackson.databind.ObjectMapper;
import com.portfolio.dto.BattleRequest;
import com.portfolio.dto.BattleResponse;
import com.portfolio.error.UpstreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 모델 API 를 가짜로 바꿔 놓고, 보낸 요청 본문과 받은 응답 처리를 본다.
// 진짜로 부르면 호출마다 요금이 나가고 답도 매번 다르다.
class BattleServiceTest {

    private StubServer stub;
    private BattleService service;
    private final ObjectMapper mapper = new ObjectMapper();

    // 가짜 서버가 받은 요청 본문. 무엇을 보냈는지 확인하는 데 쓴다
    private final AtomicReference<String> sentBody = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        service = new BattleService();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "anthropicUrl", stub.url() + "/v1/messages");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    /** POST 본문까지 받아 두는 응답기 */
    private void answer(int status, String body) {
        stub.onPost("/v1/messages", (query, requestBody) -> {
            sentBody.set(requestBody);
            return new Object[]{status, body};
        });
    }

    private static final String OK_BODY = """
            {"content":[{"type":"text","text":"그건 근거가 없다"}],"usage":{"output_tokens":42}}""";

    private BattleRequest request(String persona, String userMsg) {
        BattleRequest req = new BattleRequest();
        req.setPersona(persona);
        req.setUserMsg(userMsg);
        return req;
    }

    @Test
    @DisplayName("응답에서 답과 토큰 수를 뽑는다")
    void parsesReply() throws Exception {
        answer(200, OK_BODY);

        BattleResponse res = service.chat(request("logic", "고양이가 최고다"));

        assertThat(res.getReply()).isEqualTo("그건 근거가 없다");
        assertThat(res.getTokens()).isEqualTo(42);
    }

    @Test
    @DisplayName("이전 대화 뒤에 이번 메시지를 붙여 보낸다")
    void appendsHistory() throws Exception {
        answer(200, OK_BODY);

        BattleRequest req = request("logic", "이번 말");
        List<BattleRequest.Message> history = new ArrayList<>();
        BattleRequest.Message m = new BattleRequest.Message();
        m.setRole("assistant");
        m.setContent("지난 말");
        history.add(m);
        req.setHistory(history);

        service.chat(req);

        var sent = mapper.readTree(sentBody.get());
        assertThat(sent.path("messages")).hasSize(2);
        assertThat(sent.path("messages").get(0).path("role").asString()).isEqualTo("assistant");
        assertThat(sent.path("messages").get(0).path("content").asString()).isEqualTo("지난 말");
        // 이번 메시지는 맨 뒤에 user 로 붙는다
        assertThat(sent.path("messages").get(1).path("role").asString()).isEqualTo("user");
        assertThat(sent.path("messages").get(1).path("content").asString()).isEqualTo("이번 말");
    }

    @Test
    @DisplayName("성향 값에 따라 시스템 프롬프트가 갈린다")
    void picksSystemPrompt() throws Exception {
        answer(200, OK_BODY);

        service.chat(request("empathy", "한 마디"));
        String empathy = mapper.readTree(sentBody.get()).path("system").asString();

        service.chat(request("logic", "한 마디"));
        String logic = mapper.readTree(sentBody.get()).path("system").asString();

        assertThat(empathy).contains("창의적이고 감성적인");
        assertThat(logic).contains("논리적이고 냉철한");
        // 성향을 안 주면 논리형으로 간다
        service.chat(request(null, "한 마디"));
        assertThat(mapper.readTree(sentBody.get()).path("system").asString()).isEqualTo(logic);
    }

    @Test
    @DisplayName("출력 길이 상한을 요청에 실어 보낸다")
    void sendsMaxTokens() throws Exception {
        answer(200, OK_BODY);

        service.chat(request("logic", "한 마디"));

        assertThat(mapper.readTree(sentBody.get()).path("max_tokens").asInt()).isEqualTo(400);
    }

    @Test
    @DisplayName("429 는 QUOTA_EXCEEDED 로 바꾸고 원문은 안 싣는다")
    void quota() {
        answer(429, "{\"error\":{\"message\":\"rate limit for org abc123\"}}");

        assertThatThrownBy(() -> service.chat(request("logic", "한 마디")))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("QUOTA_EXCEEDED");
                    assertThat(e.getMessage()).doesNotContain("org abc123");
                });
    }

    @Test
    @DisplayName("그 밖의 실패는 502 로 바꾼다")
    void otherFailure() {
        answer(500, "{\"error\":{\"message\":\"internal\"}}");

        assertThatThrownBy(() -> service.chat(request("logic", "한 마디")))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                });
    }

    @Test
    @DisplayName("200 인데 답 칸이 비어 있으면 502 로 바꾼다")
    void emptyContent() {
        // content 가 빈 배열이면 예전에는 get(0) 이 null 이라 NPE 가 났다
        answer(200, "{\"content\":[],\"usage\":{\"output_tokens\":0}}");

        assertThatThrownBy(() -> service.chat(request("logic", "한 마디")))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                });
    }

    @Test
    @DisplayName("200 인데 답 칸에 text 가 없으면 502 로 바꾼다")
    void contentWithoutText() {
        answer(200, "{\"content\":[{\"type\":\"tool_use\",\"id\":\"t1\"}],\"usage\":{\"output_tokens\":3}}");

        assertThatThrownBy(() -> service.chat(request("logic", "한 마디")))
                .isInstanceOfSatisfying(UpstreamException.class, e ->
                        assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR"));
    }

    @Test
    @DisplayName("200 인데 본문이 JSON 이 아니면 502 로 바꾼다")
    void brokenSuccessBody() {
        answer(200, "<html>점검 중</html>");

        assertThatThrownBy(() -> service.chat(request("logic", "한 마디")))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                    assertThat(e.getMessage()).doesNotContain("점검 중");
                });
    }
}
