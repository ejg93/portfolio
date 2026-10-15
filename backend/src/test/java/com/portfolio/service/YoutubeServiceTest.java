package com.portfolio.service;

import tools.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 컨트롤러 테스트는 서비스를 가짜로 바꾼다. 여기서는 그 반대로 서비스를 진짜로 돌리고
// 유튜브 쪽만 가짜 서버로 바꿔서, 응답을 어떻게 읽고 실패를 어떻게 가르는지 본다.
class YoutubeServiceTest {

    private StubServer stub;
    private YoutubeService service;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        service = new YoutubeService();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "baseUrl", stub.url());
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    private void videoTitle(String title) {
        stub.on("/videos", q -> new Object[]{200, """
                {"items":[{"snippet":{"title":"%s"}}]}""".formatted(title)});
    }

    // 유튜브가 주는 실패 본문. message 에 HTML 태그가 섞여 오는 것까지 흉내 낸다
    private static String errorBody(String reason) {
        return """
                {"error":{"code":403,"message":"<code><a href=\\"/x\\">videoId</a></code> 어쩌고",
                 "errors":[{"reason":"%s"}]}}""".formatted(reason);
    }

    @Test
    @DisplayName("다음 페이지가 있으면 끝까지 따라가 댓글을 모은다")
    void paging() throws Exception {
        videoTitle("테스트 영상");
        stub.on("/commentThreads", q -> {
            boolean second = q.contains("pageToken=PAGE2");
            String items = second
                    ? """
                      {"id":"c3","snippet":{"totalReplyCount":0,"topLevelComment":{"snippet":
                       {"authorDisplayName":"셋","textDisplay":"세 번째","likeCount":3,
                        "publishedAt":"2026-01-03","updatedAt":"2026-01-03"}}}}"""
                    : """
                      {"id":"c1","snippet":{"totalReplyCount":2,"topLevelComment":{"snippet":
                       {"authorDisplayName":"하나","textDisplay":"첫 번째","likeCount":1,
                        "publishedAt":"2026-01-01","updatedAt":"2026-01-01"}}}}""";
            String next = second ? "" : ",\"nextPageToken\":\"PAGE2\"";
            return new Object[]{200, """
                    {"pageInfo":{"totalResults":42},"items":[%s]%s}""".formatted(items, next)};
        });

        ObjectNode result = service.fetchComments("abc123");

        assertThat(result.path("videoTitle").asString()).isEqualTo("테스트 영상");
        // total 은 첫 페이지 값을 쥔다. 두 번째 페이지가 덮어쓰면 안 된다
        assertThat(result.path("total").asInt()).isEqualTo(42);
        assertThat(result.path("comments")).hasSize(2);
        assertThat(result.path("comments").get(0).path("author").asString()).isEqualTo("하나");
        assertThat(result.path("comments").get(0).path("replyCount").asInt()).isEqualTo(2);
        assertThat(result.path("comments").get(1).path("text").asString()).isEqualTo("세 번째");

        // 페이지 토큰을 달고 한 번 더 불렀는지
        assertThat(stub.requests()).anyMatch(r -> r.contains("/commentThreads") && r.contains("pageToken=PAGE2"));
    }

    @Test
    @DisplayName("videoId 에 든 & 와 = 가 파라미터 경계로 안 읽힌다")
    void encodesVideoId() throws Exception {
        videoTitle("");
        stub.on("/commentThreads", q -> new Object[]{200, """
                {"pageInfo":{"totalResults":0},"items":[]}"""});

        service.fetchComments("abc&key=stolen");

        String call = stub.requests().stream()
                .filter(r -> r.startsWith("/commentThreads"))
                .findFirst()
                .orElseThrow();
        // 인코딩이 빠지면 key 파라미터가 하나 더 생겨 우리 키를 덮어쓴다
        assertThat(call).contains("videoId=abc%26key%3Dstolen");
        assertThat(call).doesNotContain("key=stolen");
    }

    @Test
    @DisplayName("할당량이 떨어지면 429 QUOTA_EXCEEDED 로 바꾼다")
    void quota() {
        videoTitle("");
        stub.on("/commentThreads", q -> new Object[]{403, errorBody("quotaExceeded")});

        assertThatThrownBy(() -> service.fetchComments("abc"))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getCode()).isEqualTo("QUOTA_EXCEEDED");
                    // 업스트림 원문이 그대로 새 나가면 안 된다
                    assertThat(e.getMessage()).doesNotContain("<code>");
                });
    }

    @Test
    @DisplayName("없는 영상은 404 VIDEO_NOT_FOUND 로 바꾼다")
    void notFound() {
        videoTitle("");
        stub.on("/commentThreads", q -> new Object[]{404, errorBody("videoNotFound")});

        assertThatThrownBy(() -> service.fetchComments("abc"))
                .isInstanceOfSatisfying(UpstreamException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("댓글이 꺼진 영상은 409 COMMENTS_DISABLED 로 바꾼다")
    void commentsDisabled() {
        videoTitle("");
        stub.on("/commentThreads", q -> new Object[]{403, errorBody("commentsDisabled")});

        assertThatThrownBy(() -> service.fetchComments("abc"))
                .isInstanceOfSatisfying(UpstreamException.class,
                        e -> assertThat(e.getCode()).isEqualTo("COMMENTS_DISABLED"));
    }

    @Test
    @DisplayName("모르는 이유와 JSON 이 아닌 본문은 502 로 떨어진다")
    void unknownReason() {
        videoTitle("");
        stub.on("/commentThreads", q -> new Object[]{500, "서버가 HTML 을 뱉었다"});

        assertThatThrownBy(() -> service.fetchComments("abc"))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                });
    }

    @Test
    @DisplayName("영상 제목 조회가 실패해도 댓글은 그대로 온다")
    void titleFailureIsSwallowed() throws Exception {
        stub.on("/videos", q -> new Object[]{500, "{}"});
        stub.on("/commentThreads", q -> new Object[]{200, """
                {"pageInfo":{"totalResults":1},"items":[
                 {"id":"c1","snippet":{"totalReplyCount":0,"topLevelComment":{"snippet":
                  {"authorDisplayName":"하나","textDisplay":"글","likeCount":0,
                   "publishedAt":"2026-01-01","updatedAt":"2026-01-01"}}}}]}"""});

        ObjectNode result = service.fetchComments("abc");

        // 제목은 곁가지다. 그것 때문에 본 목록이 통째로 실패하면 안 된다
        assertThat(result.path("videoTitle").asString()).isEmpty();
        assertThat(result.path("comments")).hasSize(1);
    }

    @Test
    @DisplayName("답글은 replies 배열로 정리해 돌려준다")
    void replies() throws Exception {
        stub.on("/comments", q -> new Object[]{200, """
                {"items":[{"id":"r1","snippet":{"authorDisplayName":"답","textDisplay":"답글",
                 "likeCount":5,"publishedAt":"2026-01-02"}}]}"""});

        ObjectNode result = service.fetchReplies("c1");

        assertThat(result.path("replies")).hasSize(1);
        assertThat(result.path("replies").get(0).path("likeCount").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 페이지 토큰이 다시 오면 수집을 멈춘다")
    void stopsOnRepeatedPageToken() throws Exception {
        videoTitle("테스트 영상");
        // 댓글은 안 주면서 같은 토큰만 돌려주는 응답. 댓글 수만 세면 이 호출이 안 끝난다
        stub.on("/commentThreads", q -> new Object[]{200, """
                {"pageInfo":{"totalResults":0},"items":[],"nextPageToken":"SAME"}"""});

        ObjectNode result = service.fetchComments("vid");

        assertThat(result.path("comments")).isEmpty();
        assertThat(commentThreadCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("다음 장이 끝없이 이어지면 50장에서 멈춘다")
    void stopsAtPageCap() throws Exception {
        videoTitle("테스트 영상");
        AtomicInteger page = new AtomicInteger();
        // 매번 다른 토큰을 주면 토큰 중복으로는 못 잡는다. 장수 상한이 받는 자리다
        stub.on("/commentThreads", q -> new Object[]{200, """
                {"pageInfo":{"totalResults":0},"items":[],"nextPageToken":"T%d"}"""
                .formatted(page.incrementAndGet())});

        service.fetchComments("vid");

        assertThat(commentThreadCalls()).isEqualTo(50);
    }

    @Test
    @DisplayName("200 인데 본문이 JSON 이 아니면 502 로 바꾼다")
    void brokenSuccessBody() {
        videoTitle("테스트 영상");
        stub.on("/commentThreads", q -> new Object[]{200, "<html>점검 중</html>"});

        assertThatThrownBy(() -> service.fetchComments("vid"))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getCode()).isEqualTo("UPSTREAM_ERROR");
                    assertThat(e.getMessage()).doesNotContain("점검 중");
                });
    }

    private long commentThreadCalls() {
        return stub.requests().stream().filter(r -> r.startsWith("/commentThreads")).count();
    }
}
