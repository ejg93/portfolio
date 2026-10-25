package com.portfolio.cheongyak;

import com.portfolio.error.ApiError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 수집 시작. GitHub Actions cron 만 부른다 — 토큰 없이는 못 연다(청약홈 호출 할당량을 남이 못 쓰게).
 * 202 로 바로 돌려주고, 끝났는지는 GET /api/cheongyak/runs/{id} 로 본다.
 */
@Slf4j
@RestController
@RequestMapping("/api/cheongyak")
@RequiredArgsConstructor
public class CollectController {

    private final CollectService collectService;

    @Value("${cheongyak.collect.token:}")
    private String token;

    @PostMapping("/collect")
    public ResponseEntity<?> collect(@RequestHeader(value = "X-Collect-Token", required = false) String given,
                                     @RequestParam(defaultValue = "manual") String src) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("COLLECT_DISABLED", "수집 토큰이 없다"));
        }
        if (given == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("UNAUTHORIZED", "토큰이 다르다"));
        }
        try {
            CollectRun run = collectService.start("cron".equals(src) ? "cron" : "manual");
            log.info("수집 {} 시작({})", run.id(), run.triggerSrc());
            return ResponseEntity.accepted().body(run);
        } catch (CollectBusyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("COLLECT_RUNNING", e.getMessage()));
        } catch (DataAccessException e) {
            log.error("수집 시작 중 저장소 오류", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("DB_UNAVAILABLE", "저장소에 연결하지 못했다"));
        }
    }
}
