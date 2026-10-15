package com.portfolio.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.portfolio.dto.BattleRequest;
import com.portfolio.error.UpstreamException;
import com.portfolio.dto.BattleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class BattleService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    // 테스트가 가짜 서버로 갈아끼운다. 평소에는 이 기본값을 쓴다
    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String anthropicUrl;


    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 400;

    // 양쪽 다 같은 모델을 쓴다. 비싼 모델 둘을 붙이면 한 판에 드는 비용이 커서 내렸다.
    // 갈리는 것은 시스템 프롬프트뿐이라, 화면도 모델 이름 대신 성향으로 적는다.
    private static final String MODEL = "claude-haiku-4-5-20251001";


    private static final String SYSTEM_LOGIC =
            "너는 논리적이고 냉철한 토론자다. " +
                    "데이터와 근거를 바탕으로 명확하게 주장하며, 감정보다 이성을 앞세운다. " +
                    "반드시 150자 이내로 완성된 문장으로 끝내라. " +
                    "150자가 넘어갈 것 같으면 핵심만 추려서 짧게 마무리해라. " +
                    "문장이 중간에 잘리는 것은 절대 허용되지 않는다." +
                    "문장이 길어지지않도록 반말을 쓰고 축약어를 써라.";

    private static final String SYSTEM_EMPATHY =
            "너는 창의적이고 감성적인 토론자다. " +
                    "공감과 스토리텔링으로 상대를 설득하며, 직관과 상상력을 중시한다. " +
                    "반드시 150자 이내로 완성된 문장으로 끝내라. " +
                    "150자가 넘어갈 것 같으면 핵심만 추려서 짧게 마무리해라. " +
                    "문장이 중간에 잘리는 것은 절대 허용되지 않는다." +
                    "문장이 길어지지않도록 반말을 쓰고 축약어를 써라.";

    // 모델 응답은 몇십 초가 걸린다. 그래도 상한이 없으면 업스트림이 안 끊을 때 스레드가 그대로 물린다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public BattleResponse chat(BattleRequest req) throws Exception {
        String systemPrompt = resolveSystem(req.getPersona());

        ArrayNode messages = mapper.createArrayNode();
        if (req.getHistory() != null) {
            for (BattleRequest.Message m : req.getHistory()) {
                ObjectNode node = mapper.createObjectNode();
                node.put("role", m.getRole());
                node.put("content", m.getContent());
                messages.add(node);
            }
        }
        ObjectNode userNode = mapper.createObjectNode();
        userNode.put("role", "user");
        userNode.put("content", req.getUserMsg());
        messages.add(userNode);

        ObjectNode body = mapper.createObjectNode();
        body.put("model", MODEL);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.set("messages", messages);

        String requestBody = mapper.writeValueAsString(body);
        log.debug("Claude API 요청 - persona: {}", req.getPersona());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(anthropicUrl))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // 본문에는 키·조직 정보가 섞여 나올 수 있다. 로그로만 남긴다
            log.error("Claude API 오류: {} - {}", response.statusCode(), response.body());
            HttpStatus status = response.statusCode() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
            String code = response.statusCode() == 429 ? "QUOTA_EXCEEDED" : "UPSTREAM_ERROR";
            throw new UpstreamException(status, code, "모델 API 응답이 정상이 아니다");
        }

        JsonNode result;
        try {
            result = mapper.readTree(response.body());
        } catch (JacksonException e) {
            log.error("Claude API 200 인데 본문이 JSON 이 아니다 - {}자", response.body().length());
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "모델 API 응답이 정상이 아니다");
        }

        // content 가 비어 오면 get(0) 이 null 이라 여기서 NPE 가 났다. 업스트림 실패로 모은다
        JsonNode first = result.path("content").path(0);
        if (first.path("text").isMissingNode()) {
            log.error("Claude API 응답에 답이 없다 - content {}칸", result.path("content").size());
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "모델 API 응답이 정상이 아니다");
        }

        String reply = first.path("text").asString("");
        int tokens = result.path("usage").path("output_tokens").asInt(0);

        log.debug("Claude API 응답 - tokens: {}, reply: {}", tokens, reply);
        return new BattleResponse(reply, tokens);
    }

    // 성향 값이 없거나 모르는 값이면 논리형으로 간다
    private String resolveSystem(String persona) {
        return "empathy".equalsIgnoreCase(persona) ? SYSTEM_EMPATHY : SYSTEM_LOGIC;
    }
}