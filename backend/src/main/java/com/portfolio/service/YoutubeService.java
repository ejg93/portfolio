package com.portfolio.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class YoutubeService {

    @Value("${youtube.api.key}")
    private String apiKey;

    // 테스트가 가짜 서버로 갈아끼운다. 평소에는 이 기본값을 쓴다
    @Value("${youtube.api.base-url:https://www.googleapis.com/youtube/v3}")
    private String baseUrl;

    private static final int MAX_COMMENTS = 1000;
    private static final int PAGE_SIZE    = 100;
    // 댓글 수만 세면 빈 페이지가 이어질 때 수집이 안 끝난다. 장수도 같이 센다
    private static final int MAX_PAGES    = 50;


    // 타임아웃이 없으면 업스트림이 안 끊을 때 톰캣 스레드가 그대로 물린다
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(20);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    // ── 댓글 수집 ─────────────────────────────────────────
    public ObjectNode fetchComments(String videoId) throws Exception {
        String videoTitle = fetchVideoTitle(videoId);
        List<ObjectNode> comments = new ArrayList<>();
        String pageToken = null;
        Set<String> seenTokens = new HashSet<>();
        int totalCount = 0;
        int pages = 0;

        do {
            String url = baseUrl + "/commentThreads"
                    + "?part=snippet"
                    + "&videoId=" + enc(videoId)
                    + "&maxResults=" + PAGE_SIZE
                    + "&order=relevance"
                    + "&key=" + enc(apiKey)
                    + (pageToken != null ? "&pageToken=" + enc(pageToken) : "");

            JsonNode res = get(url);

            if (totalCount == 0) {
                totalCount = res.path("pageInfo").path("totalResults").asInt(0);
            }

            for (JsonNode item : res.path("items")) {
                JsonNode snippet = item.path("snippet").path("topLevelComment").path("snippet");
                ObjectNode c = mapper.createObjectNode();
                c.put("id",          item.path("id").asString(""));
                c.put("author",      snippet.path("authorDisplayName").asString(""));
                c.put("text",        snippet.path("textDisplay").asString(""));
                c.put("likeCount",   snippet.path("likeCount").asInt(0));
                c.put("publishedAt", snippet.path("publishedAt").asString(""));
                c.put("updatedAt",   snippet.path("updatedAt").asString(""));
                c.put("replyCount",  item.path("snippet").path("totalReplyCount").asInt(0));
                comments.add(c);
            }

            pageToken = res.path("nextPageToken").isMissingNode()
                    ? null : res.path("nextPageToken").asString("");
            pages++;

            // 같은 토큰을 다시 주면 다음 장이 없다는 뜻이다. 그대로 따라가면 같은 요청을 끝없이 반복한다
            if (pageToken != null && !seenTokens.add(pageToken)) {
                log.warn("같은 페이지 토큰이 다시 왔다 - {}개에서 멈춘다", comments.size());
                pageToken = null;
            }
            if (pageToken != null && pages >= MAX_PAGES) {
                log.warn("페이지 {}장에 걸렸다 - {}개에서 멈춘다", MAX_PAGES, comments.size());
                pageToken = null;
            }

            log.debug("댓글 수집 중: {}개", comments.size());

        } while (pageToken != null && comments.size() < MAX_COMMENTS);

        ObjectNode result = mapper.createObjectNode();
        result.put("videoTitle", videoTitle);
        result.put("total", totalCount);
        ArrayNode arr = mapper.createArrayNode();
        comments.forEach(arr::add);
        result.set("comments", arr);
        return result;
    }

    // ── 답글 수집 (클릭 시 호출) ──────────────────────────
    public ObjectNode fetchReplies(String commentId) throws Exception {
        String url = baseUrl + "/comments"
                + "?part=snippet"
                + "&parentId=" + enc(commentId)
                + "&maxResults=100"
                + "&key=" + enc(apiKey);

        JsonNode res = get(url);
        List<ObjectNode> replies = new ArrayList<>();

        for (JsonNode item : res.path("items")) {
            JsonNode snippet = item.path("snippet");
            ObjectNode r = mapper.createObjectNode();
            r.put("id",          item.path("id").asString(""));
            r.put("author",      snippet.path("authorDisplayName").asString(""));
            r.put("text",        snippet.path("textDisplay").asString(""));
            r.put("likeCount",   snippet.path("likeCount").asInt(0));
            r.put("publishedAt", snippet.path("publishedAt").asString(""));
            replies.add(r);
        }

        ObjectNode result = mapper.createObjectNode();
        ArrayNode arr = mapper.createArrayNode();
        replies.forEach(arr::add);
        result.set("replies", arr);
        return result;
    }

    // ── 영상 제목 ─────────────────────────────────────────
    private String fetchVideoTitle(String videoId) {
        try {
            String url = baseUrl + "/videos"
                    + "?part=snippet"
                    + "&id=" + enc(videoId)
                    + "&key=" + enc(apiKey);
            JsonNode res = get(url);
            JsonNode items = res.path("items");
            if (items.size() == 0) return "";
            return items.get(0).path("snippet").path("title").asString("");
        } catch (Exception e) {
            log.warn("영상 제목 조회 실패: {}", e.getMessage());
            return "";
        }
    }

    // 쿼리 문자열에 그대로 이어 붙이면 값 안의 & 나 = 가 파라미터 경계로 읽힌다
    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    // ── HTTP GET ──────────────────────────────────────────
    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw classify(res.statusCode(), res.body());
        }
        try {
            return mapper.readTree(res.body());
        } catch (JacksonException e) {
            // 점검 안내 HTML 이 200 으로 오는 경우다. 그대로 두면 파서 예외가 500 처럼 보인다
            log.warn("YouTube API 200 인데 본문이 JSON 이 아니다 - {}자", res.body().length());
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "YouTube API 응답이 정상이 아니다");
        }
    }

    // YouTube 가 준 reason 으로 갈라 우리 코드로 바꾼다. 원문은 로그로만 남긴다 —
    // 응답 본문에 HTML 태그와 내부 사정이 섞여 있어서 그대로 내보내면 화면에 그게 뜬다.
    private UpstreamException classify(int status, String body) {
        String reason = "";
        String upstreamMessage = "";
        try {
            JsonNode err = mapper.readTree(body).path("error");
            reason = err.path("errors").path(0).path("reason").asString("");
            upstreamMessage = err.path("message").asString("");
        } catch (Exception ignored) {
            // 본문이 JSON 이 아니면 상태코드만 보고 가른다
        }
        log.warn("YouTube API {} reason={} message={}", status, reason, upstreamMessage);

        return switch (reason) {
            case "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded" ->
                    new UpstreamException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                            "YouTube API 하루 할당량을 다 썼다");
            case "videoNotFound", "commentThreadNotFound", "parentNotFound" ->
                    new UpstreamException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND",
                            "그 ID 로 영상이나 댓글을 못 찾았다");
            case "commentsDisabled" ->
                    new UpstreamException(HttpStatus.CONFLICT, "COMMENTS_DISABLED",
                            "그 영상은 댓글이 꺼져 있다");
            default -> new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR",
                    "YouTube API 응답이 정상이 아니다");
        };
    }
}
