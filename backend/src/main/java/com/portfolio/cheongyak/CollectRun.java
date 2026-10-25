package com.portfolio.cheongyak;

import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 수집 실행 한 번의 기록. V1 의 cheongyak_collect_run 컬럼과 1:1.
 * status 는 RUNNING → OK | FAIL. 화면 「수집 이력」 과 Actions 폴링이 이 값을 본다.
 */
@Builder(toBuilder = true)
public record CollectRun(
        Long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String status,
        String triggerSrc,
        LocalDate sinceDate,
        int fetched,
        int inserted,
        int updated,
        int remndrCount,
        int apiCalls,
        int docFetched,
        int docOk,
        int docPending,
        String errorMessage
) {

    public static final String RUNNING = "RUNNING";
    public static final String OK = "OK";
    public static final String FAIL = "FAIL";
}
