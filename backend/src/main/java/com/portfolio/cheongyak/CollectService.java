package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 청약 공고 수집. 시작(start)은 RUNNING 기록만 남기고 바로 돌아가고, 본체(run)는 작업 스레드 하나에서 돈다.
 * Railway HTTP 요청은 최대 15분이라 첫 백필을 요청 하나로 기다리면 끊길 수 있다 — Actions 가 run 상태를 폴링한다.
 *
 * 트랜잭션은 저장소의 공고 한 건 단위다. 여기에 @Transactional 을 붙이면 수집 전체가 한 트랜잭션이 되어
 * 중간 실패가 앞 건까지 날린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private static final List<ApplyhomeKind> KINDS = List.of(
            ApplyhomeKind.APT, ApplyhomeKind.REMNDR, ApplyhomeKind.URBTY, ApplyhomeKind.PBLPVT, ApplyhomeKind.OPT);
    // 이보다 오래 RUNNING 이면 재기동 등으로 버려진 것으로 본다
    private static final long STALE_MINUTES = 60;
    private static final int FIRST_WINDOW_DAYS = 365;
    // 접수일 정정이 공고 뒤 한 달 남짓 이어진다
    private static final int NEXT_WINDOW_DAYS = 45;
    private static final int ERROR_MAX = 1000;

    private final NoticeRepository repo;
    private final ApplyhomeClient client;
    private final NoticeDocClient docClient;

    // 실행 한 번에 받는 공고문 수. 청약홈에 1초 간격이라 150건이면 5분 남짓
    @Value("${cheongyak.doc.batch-size:150}")
    private int docBatch;

    // 테스트는 Runnable::run 으로 바꿔 같은 스레드에서 돌린다
    private Executor worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cheongyak-collect");
        t.setDaemon(true);
        return t;
    });

    /** RUNNING 기록을 남기고 작업 스레드에 넘긴다. 이미 돌고 있으면 CollectBusyException */
    public synchronized CollectRun start(String triggerSrc) {
        repo.failStaleRuns(NoticeRepository.now().minusMinutes(STALE_MINUTES));
        if (repo.hasRunning()) {
            throw new CollectBusyException();
        }
        LocalDate today = NoticeRepository.now().toLocalDate();
        LocalDate since = today.minusDays(repo.lastSuccessfulSince().isPresent() ? NEXT_WINDOW_DAYS : FIRST_WINDOW_DAYS);
        CollectRun run = CollectRun.builder().startedAt(NoticeRepository.now()).status(CollectRun.RUNNING)
                .triggerSrc(triggerSrc).sinceDate(since).build();
        long id = repo.insertRun(run);
        worker.execute(() -> {
            try {
                run(id, since);
            } catch (RuntimeException e) {
                log.error("수집 실행 {} 마무리 실패", id, e);
            }
        });
        return run.toBuilder().id(id).build();
    }

    /** 수집 본체. API 단계가 실패하면 FAIL, 공고문 단계 실패는 건수로만 남는다 */
    public CollectRun run(long id, LocalDate since) {
        long t0 = System.currentTimeMillis();
        client.reset();
        int fetched = 0;
        int inserted = 0;
        int updated = 0;
        int remndr = 0;
        try {
            for (ApplyhomeKind kind : KINDS) {
                for (JsonNode d : client.fetchDetails(kind, since)) {
                    Notice n = NoticeMapper.toNotice(kind, d);
                    if (n.houseManageNo() == null || n.pblancNo() == null) {
                        continue;
                    }
                    int r = repo.saveNoticeWithTypes(n,
                            () -> NoticeMapper.toHouseTypes(kind, n, client.fetchHouseTypes(kind, n.pblancNo())));
                    fetched++;
                    inserted += r == 1 ? 1 : 0;
                    updated += r == 2 ? 1 : 0;
                    remndr += kind == ApplyhomeKind.REMNDR ? 1 : 0;
                }
            }
        } catch (RuntimeException e) {
            log.warn("수집 {} 실패 — {}건 받은 뒤 {}", id, fetched, e.getClass().getSimpleName());
            repo.finishRun(id, CollectRun.builder().finishedAt(NoticeRepository.now()).status(CollectRun.FAIL)
                    .fetched(fetched).inserted(inserted).updated(updated).remndrCount(remndr)
                    .apiCalls(client.callCount()).docPending(repo.countJudgeTargetsWithoutDoc())
                    .errorMessage(describe(e)).build());
            return repo.findRun(id).orElseThrow();
        }

        int docFetched = 0;
        int docOk = 0;
        try {
            for (Notice n : repo.judgeTargetsWithoutDoc(docBatch)) {
                NoticeDoc doc = docClient.fetch(n.kind(), n.pblancUrl());
                try {
                    repo.updateDoc(n.houseManageNo(), n.pblancNo(), doc);
                } catch (DataIntegrityViolationException e) {
                    // 추출값이 컬럼에 안 들어가는 공고 하나 때문에 다음 공고까지 멈추지 않게 표시만 하고 넘어간다.
                    // 표시를 안 하면 다음 실행도 같은 공고에서 걸린다
                    log.warn("수집 {} 공고문 저장 실패 {}:{} — {}", id, n.houseManageNo(), n.pblancNo(), e.getClass().getSimpleName());
                    doc = NoticeDoc.failed(NoticeDoc.SAVE_ERR);
                    repo.updateDoc(n.houseManageNo(), n.pblancNo(), doc);
                }
                docFetched++;
                docOk += NoticeDoc.OK.equals(doc.status()) ? 1 : 0;
            }
        } catch (RuntimeException e) {
            log.warn("수집 {} 공고문 단계 중단 — {}", id, e.getClass().getSimpleName());
        }

        repo.finishRun(id, CollectRun.builder().finishedAt(NoticeRepository.now()).status(CollectRun.OK)
                .fetched(fetched).inserted(inserted).updated(updated).remndrCount(remndr)
                .apiCalls(client.callCount()).docFetched(docFetched).docOk(docOk)
                .docPending(repo.countJudgeTargetsWithoutDoc()).build());
        log.info("수집 {} 끝 — 받음 {} 신규 {} 갱신 {} 공고문 {}/{} {}ms",
                id, fetched, inserted, updated, docOk, docFetched, System.currentTimeMillis() - t0);
        return repo.findRun(id).orElseThrow();
    }

    @PreDestroy
    void shutdown() {
        if (worker instanceof ExecutorService es) {
            es.shutdownNow();
        }
    }

    private static String describe(RuntimeException e) {
        String msg = e instanceof UpstreamException u ? u.getCode() + ": " + u.getMessage()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
        return msg.length() > ERROR_MAX ? msg.substring(0, ERROR_MAX) : msg;
    }
}
