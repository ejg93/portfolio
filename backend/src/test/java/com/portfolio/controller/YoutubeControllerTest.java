package com.portfolio.controller;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.portfolio.error.UpstreamException;
import com.portfolio.service.YoutubeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(YoutubeController.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:3000")
class YoutubeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private YoutubeService youtubeService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("댓글 요청은 서비스가 만든 JSON 을 그대로 내보낸다")
    void comments() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("videoTitle", "테스트 영상");
        body.put("total", 2);
        body.set("comments", mapper.createArrayNode());

        given(youtubeService.fetchComments("abc123")).willReturn(body);

        mvc.perform(get("/api/youtube/comments").param("videoId", "abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoTitle").value("테스트 영상"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.comments").isArray());
    }

    @Test
    @DisplayName("videoId 가 없으면 400 이다")
    void commentsWithoutVideoId() throws Exception {
        mvc.perform(get("/api/youtube/comments"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("할당량이 떨어지면 429 에 QUOTA_EXCEEDED 로 나간다")
    void commentsQuota() throws Exception {
        given(youtubeService.fetchComments(anyString()))
                .willThrow(new UpstreamException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                        "YouTube API 하루 할당량을 다 썼다"));

        mvc.perform(get("/api/youtube/comments").param("videoId", "abc123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("YouTube API 하루 할당량을 다 썼다"));
    }

    @Test
    @DisplayName("없는 영상이면 404 에 VIDEO_NOT_FOUND 로 나간다")
    void commentsNotFound() throws Exception {
        given(youtubeService.fetchComments(anyString()))
                .willThrow(new UpstreamException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND",
                        "그 ID 로 영상이나 댓글을 못 찾았다"));

        mvc.perform(get("/api/youtube/comments").param("videoId", "zzzzzzzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
    }

    @Test
    @DisplayName("그 밖의 업스트림 실패는 502 고 원문은 안 실린다")
    void commentsUpstreamFailure() throws Exception {
        // 업스트림 장애는 클라이언트 잘못이 아니라서 400 이 아니다.
        // YouTube 가 주는 문장에는 HTML 태그가 섞여 있어 그대로 내보내면 화면에 그게 뜬다
        given(youtubeService.fetchComments(anyString()))
                .willThrow(new RuntimeException("<code><a href=\"/youtube/v3/docs\">videoId</a></code> 어쩌고"));

        mvc.perform(get("/api/youtube/comments").param("videoId", "abc123"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(not(containsString("<code>"))));
    }

    @Test
    @DisplayName("답글 요청은 replies 배열을 돌려준다")
    void replies() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.set("replies", mapper.createArrayNode());

        given(youtubeService.fetchReplies("c1")).willReturn(body);

        mvc.perform(get("/api/youtube/replies").param("commentId", "c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replies").isArray());
    }

    @Test
    @DisplayName("commentId 가 없으면 400 이다")
    void repliesWithoutCommentId() throws Exception {
        mvc.perform(get("/api/youtube/replies"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("videoId 가 공백뿐이면 400 이고 유튜브를 안 부른다")
    void commentsBlankVideoId() throws Exception {
        // 빈 값을 그대로 넘기면 하루 할당량만 깎이고 업스트림이 404 를 돌려준다
        mvc.perform(get("/api/youtube/comments").param("videoId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(youtubeService);
    }

    @Test
    @DisplayName("commentId 가 공백뿐이면 400 이고 유튜브를 안 부른다")
    void repliesBlankCommentId() throws Exception {
        mvc.perform(get("/api/youtube/replies").param("commentId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(youtubeService);
    }

    @Test
    @DisplayName("답글 쪽 업스트림 실패도 502 고 원문은 안 실린다")
    void repliesUpstreamFailure() throws Exception {
        given(youtubeService.fetchReplies(anyString()))
                .willThrow(new RuntimeException("<code>parentId</code> 어쩌고"));

        mvc.perform(get("/api/youtube/replies").param("commentId", "c1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(not(containsString("<code>"))));
    }

    @Test
    @DisplayName("답글 할당량이 떨어지면 429 에 QUOTA_EXCEEDED 로 나간다")
    void repliesQuota() throws Exception {
        given(youtubeService.fetchReplies(anyString()))
                .willThrow(new UpstreamException(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_EXCEEDED",
                        "YouTube API 하루 할당량을 다 썼다"));

        mvc.perform(get("/api/youtube/replies").param("commentId", "c1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }
}
