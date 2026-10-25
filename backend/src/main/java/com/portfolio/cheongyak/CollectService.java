package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 청약 공고 수집. 시작(start)은 RUNNING 기록만 남기고 바로 돌아가고, 본체(run)는 작업 스레드 하나에서 돈다.
 * 단계: 공고 API → 공고문 → 결과(경쟁률·당첨가점) → 지오코딩 → 실거래 → 시세 계산. 공고 API 가 실패하면 FAIL 이고,
 * 뒤 단계는 실패해도 건수만 남기고 다음 단계로 간다. 단계마다 실행당 상한이 있어 첫 백필은 여러 실행에 나눠 채워진다.
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
    // 발표 뒤 이 안에 결과가 비어 있으면 다음 실행에 다시 본다. 지나면 NONE
    private static final int RESULT_GRACE_DAYS = 14;
    // 이보다 오래된 공고의 결과는 받지 않는다
    private static final int RESULT_WINDOW_DAYS = 365;
    // 접수가 끝난 지 이 안인 공고까지 시세를 본다
    private static final int ACTIVE_DAYS = 30;
    // 이번 달·지난달 실거래는 신고가 계속 들어오니 이 간격으로 다시 받는다
    private static final int MONTH_REFRESH_DAYS = 7;
    private static final long GAP_ODCLOUD_MS = 200;
    private static final long GAP_KAKAO_MS = 100;
    private static final long GAP_RTMS_MS = 300;
    private static final String RESULT_OK = "OK";
    private static final String RESULT_NONE = "NONE";
    private static final String GEO_OK = "OK";
    private static final String GEO_FAIL = "FAIL";
    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    private final NoticeRepository repo;
    private final ApplyhomeClient client;
    private final NoticeDocClient docClient;
    private final KakaoGeoClient geoClient;
    private final RtmsClient rtmsClient;

    // 실행 한 번에 받는 공고문 수. 청약홈에 1초 간격이라 150건이면 5분 남짓
    @Value("${cheongyak.doc.batch-size:150}")
    private int docBatch;

    // 실행 한 번에 결과를 받는 공고 수(APT 2콜·무순위 1콜)
    @Value("${cheongyak.result.batch-size:150}")
    private int resultBatch;

    @Value("${cheongyak.geo.batch-size:200}")
    private int geoBatch;

    // 실행 한 번에 부르는 실거래 API 횟수
    @Value("${cheongyak.rtms.batch-size:300}")
    private int rtmsBatch;

    @Value("${cheongyak.rtms.months:12}")
    private int rtmsMonths;

    // 시세 표본: 준공 N년 이내, 전용 ±N㎡
    @Value("${cheongyak.market.build-years:5}")
    private int buildYears;

    @Value("${cheongyak.market.area-tolerance:5}")
    private int areaTolerance;

    // 테스트는 대기 없이 돌린다
    private NoticeDocClient.Sleeper sleeper = Thread::sleep;

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

        CollectRun.CollectRunBuilder b = CollectRun.builder().finishedAt(NoticeRepository.now()).status(CollectRun.OK)
                .fetched(fetched).inserted(inserted).updated(updated).remndrCount(remndr)
                .docFetched(docFetched).docOk(docOk).docPending(repo.countJudgeTargetsWithoutDoc());
        LocalDate today = NoticeRepository.now().toLocalDate();
        results(id, today, b);
        geocode(id, b);
        trades(id, today, b);
        market(id, today, b);
        CollectRun done = b.finishedAt(NoticeRepository.now()).apiCalls(client.callCount()).build();
        repo.finishRun(id, done);
        log.info("수집 {} 끝 — 받음 {} 신규 {} 갱신 {} 공고문 {}/{} 결과 {}/{} 지오 {}/{} 실거래 {}콜 시세 {} {}ms",
                id, fetched, inserted, updated, docOk, docFetched, done.resultOk(), done.resultFetched(),
                done.geoOk(), done.geoFetched(), done.rtmsCalls(), done.mktUpdated(), System.currentTimeMillis() - t0);
        return repo.findRun(id).orElseThrow();
    }

    /** 당첨 발표가 지난 공고의 경쟁률·당첨가점. 발표 뒤 RESULT_GRACE_DAYS 안에 비어 있으면 다음 실행에 다시 본다 */
    private void results(long id, LocalDate today, CollectRun.CollectRunBuilder b) {
        LocalDate since = today.minusDays(RESULT_WINDOW_DAYS);
        int fetched = 0;
        int ok = 0;
        try {
            for (Notice n : repo.resultTargets(today, since, resultBatch)) {
                String hmn = n.houseManageNo();
                String pno = n.pblancNo();
                boolean apt = ApplyhomeKind.APT.name().equals(n.kind());
                List<CmpetRow> cmpet = client.fetchCmpet(apt ? ApplyhomeKind.APT : ApplyhomeKind.REMNDR, hmn, pno).stream()
                        .map(d -> NoticeMapper.toCmpet(hmn, pno, d)).toList();
                pause(GAP_ODCLOUD_MS);
                List<ScoreRow> scores = List.of();
                if (apt) {
                    scores = client.fetchScores(hmn, pno).stream().map(d -> NoticeMapper.toScore(hmn, pno, d)).toList();
                    pause(GAP_ODCLOUD_MS);
                }
                fetched++;
                if (!cmpet.isEmpty() || !scores.isEmpty()) {
                    repo.replaceResults(hmn, pno, cmpet, scores, RESULT_OK);
                    ok++;
                } else if (n.przwnerDe().plusDays(RESULT_GRACE_DAYS).isBefore(today)) {
                    repo.replaceResults(hmn, pno, cmpet, scores, RESULT_NONE);
                }
            }
        } catch (RuntimeException e) {
            log.warn("수집 {} 결과 단계 중단 — {}", id, e.getClass().getSimpleName());
        }
        b.resultFetched(fetched).resultOk(ok).resultPending(repo.countResultPending(today, since));
    }

    /** 공고 주소 → 법정동코드·좌표. 카카오 키가 없으면 건너뛴다 */
    private void geocode(long id, CollectRun.CollectRunBuilder b) {
        int fetched = 0;
        int ok = 0;
        if (geoClient.enabled()) {
            try {
                for (Notice n : repo.geoTargets(geoBatch)) {
                    Optional<Geo> g = geoClient.geocode(n.address(), n.houseNm());
                    pause(GAP_KAKAO_MS);
                    repo.updateGeo(n.houseManageNo(), n.pblancNo(), g.orElse(null), g.isPresent() ? GEO_OK : GEO_FAIL);
                    fetched++;
                    ok += g.isPresent() ? 1 : 0;
                }
            } catch (RuntimeException e) {
                // 키 오류·쿼터면 남은 공고를 전부 FAIL 로 찍지 않게 단계를 멈춘다
                log.warn("수집 {} 지오코딩 단계 중단 — {}", id, e.getClass().getSimpleName());
            }
        }
        b.geoFetched(fetched).geoOk(ok).geoPending(repo.countGeoPending());
    }

    /** 시세를 볼 시군구의 최근 rtmsMonths 개월 실거래. 받은 달은 다시 안 받고, 이번 달·지난달만 MONTH_REFRESH_DAYS 마다 다시 받는다 */
    private void trades(long id, LocalDate today, CollectRun.CollectRunBuilder b) {
        int calls = 0;
        int pending = 0;
        try {
            List<String> months = months(today);
            for (String lawd : repo.rtmsLawdCodes(today.minusDays(ACTIVE_DAYS))) {
                for (String ymd : months) {
                    if (fresh(lawd, ymd, months)) {
                        continue;
                    }
                    if (calls >= rtmsBatch) {
                        pending++;
                        continue;
                    }
                    RtmsClient.Fetched f = rtmsClient.fetchMonth(lawd, ymd);
                    repo.replaceRtmsMonth(lawd, ymd, f.trades(), f.calls());
                    calls += f.calls();
                    pause(GAP_RTMS_MS);
                }
            }
        } catch (RuntimeException e) {
            log.warn("수집 {} 실거래 단계 중단 — {}", id, e.getClass().getSimpleName());
        }
        b.rtmsCalls(calls).rtmsPending(pending);
    }

    /** 주택형마다 같은 동(모자라면 시군구) 신축 ±areaTolerance㎡ 실거래 중앙값. 외부 호출 없음 */
    private void market(long id, LocalDate today, CollectRun.CollectRunBuilder b) {
        int updated = 0;
        try {
            String from = YearMonth.from(today).minusMonths(rtmsMonths - 1L).format(YYYYMM);
            int builtSince = today.getYear() - buildYears;
            BigDecimal tol = BigDecimal.valueOf(areaTolerance);
            for (MarketTarget t : repo.marketTargets(today.minusDays(ACTIVE_DAYS))) {
                BigDecimal lo = t.exclusiveArea().subtract(tol);
                BigDecimal hi = t.exclusiveArea().add(tol);
                List<Long> dong = t.umdNm() == null ? List.of() : repo.tradeAmounts(t.lawdCd(), t.umdNm(), lo, hi, builtSince, from);
                List<Long> sgg = repo.tradeAmounts(t.lawdCd(), null, lo, hi, builtSince, from);
                MarketPricer.Market m = MarketPricer.pick(dong, sgg);
                repo.updateMarket(t.houseManageNo(), t.pblancNo(), t.modelNo(), m.median(), m.count(), m.basis());
                updated++;
            }
        } catch (RuntimeException e) {
            log.warn("수집 {} 시세 단계 중단 — {}", id, e.getClass().getSimpleName());
        }
        b.mktUpdated(updated);
    }

    /** 이번 달부터 거꾸로 rtmsMonths 개(YYYYMM) */
    private List<String> months(LocalDate today) {
        YearMonth now = YearMonth.from(today);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rtmsMonths; i++) {
            out.add(now.minusMonths(i).format(YYYYMM));
        }
        return out;
    }

    /** 이미 받은 달인가. 앞의 두 달(이번 달·지난달)은 신고가 계속 들어오니 MONTH_REFRESH_DAYS 가 지나면 다시 받는다 */
    private boolean fresh(String lawd, String ymd, List<String> months) {
        Optional<LocalDateTime> at = repo.rtmsMonthFetchedAt(lawd, ymd);
        if (at.isEmpty()) {
            return false;
        }
        boolean recent = months.indexOf(ymd) < 2;
        return !recent || at.get().isAfter(NoticeRepository.now().minusDays(MONTH_REFRESH_DAYS));
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("수집 중단");
        }
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
