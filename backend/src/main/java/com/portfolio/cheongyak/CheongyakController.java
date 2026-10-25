package com.portfolio.cheongyak;

import com.portfolio.cheongyak.dto.JudgeRequest;
import com.portfolio.cheongyak.dto.NoticeView;
import com.portfolio.cheongyak.rules.Applicant;
import com.portfolio.cheongyak.rules.RuleTables;
import com.portfolio.error.ApiError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 청약 공고 조회·판정·수집 이력·규칙 표. 판정 요청 본문(신청자 조건)은 로그에 남기지 않는다 */
@Slf4j
@RestController
@RequestMapping("/api/cheongyak")
@RequiredArgsConstructor
public class CheongyakController {

    private final CheongyakQueryService service;

    @GetMapping("/notices")
    public ResponseEntity<?> notices(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "capital") String area,
                                     @RequestParam(required = false) String kind) {
        if (!"capital".equals(area) && !"all".equals(area) && !RuleTables.SIDOS.contains(area)) {
            return badRequest("area 는 capital·all·시도 이름 중 하나다");
        }
        Set<String> kinds = kind == null || kind.isBlank() ? Set.of()
                : Arrays.stream(kind.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<NoticeView> list = service.notices(from, to, area, kinds);
        return ResponseEntity.ok(Map.of("count", list.size(), "notices", list));
    }

    @PostMapping("/judge")
    public ResponseEntity<?> judge(@RequestBody JudgeRequest req) {
        String invalid = validate(req == null ? null : req.applicant());
        if (invalid != null) {
            return badRequest(invalid);
        }
        Applicant a = req.applicant();
        // 시군은 경기 거주자만 쓴다. 다른 시도에서 넘어온 값은 버린다
        if (!"경기".equals(a.residenceSido()) && a.residenceCity() != null) {
            a = a.toBuilder().residenceCity(null).build();
        }
        int n = req.notices() == null ? 0 : req.notices().size();
        log.info("판정 요청 {}건", n);
        return ResponseEntity.ok(service.judge(new JudgeRequest(a, req.notices())));
    }

    @GetMapping("/runs")
    public ResponseEntity<?> runs(@RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(Map.of("runs", service.runs(limit)));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<?> run(@PathVariable long id) {
        return service.run(id).<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("NOT_FOUND", "수집 이력이 없다")));
    }

    @GetMapping("/rules")
    public ResponseEntity<?> rules() {
        return ResponseEntity.ok(service.rules());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> dbDown(DataAccessException e) {
        log.error("청약 저장소 오류", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("DB_UNAVAILABLE", "저장소에 연결하지 못했다"));
    }

    // Jackson 3 은 빠진 원시 타입 칸을 0 으로 채우지 않고 실패한다 — 판정 요청은 신청자 칸을 전부 보내야 한다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return badRequest("요청 본문을 읽지 못했다 — applicant 의 칸을 전부 보낸다");
    }

    private static String validate(Applicant a) {
        if (a == null) {
            return "applicant 가 없다";
        }
        if (a.residenceSido() == null || !RuleTables.SIDOS.contains(a.residenceSido())) {
            return "residenceSido 는 17개 시도 이름 중 하나다";
        }
        if (a.birthDate() == null) {
            return "birthDate 가 없다";
        }
        if (a.residenceCity() != null && "경기".equals(a.residenceSido()) && !RuleTables.GG_CITIES.contains(a.residenceCity())) {
            return "residenceCity 는 경기 시군 이름(「시」「군」 뺀 것) 중 하나다";
        }
        return null;
    }

    private static ResponseEntity<ApiError> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", message));
    }
}
