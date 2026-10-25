package com.portfolio.cheongyak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// H2(PostgreSQL 모드)에 실제로 넣고 꺼낸다. 같은 SQL 이 운영 Postgres 에서도 돌아야 한다
@SpringBootTest
@ActiveProfiles("test")
class NoticeRepositoryTest {

    @Autowired
    private NoticeRepository repo;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM cheongyak_house_type").update();
        jdbc.sql("DELETE FROM cheongyak_notice").update();
        jdbc.sql("DELETE FROM cheongyak_collect_run").update();
    }

    @Test
    @DisplayName("같은 raw 로 두 번 넣으면 1, 0 — 두 번째는 updated_at 도 그대로")
    void upsertSameRawTwice() {
        Notice n = Fixtures.apt("A1").build();
        assertThat(repo.upsertNotice(n)).isEqualTo(1);
        LocalDateTime updated = repo.findNotice("A1", "A1").orElseThrow().updatedAt();
        assertThat(repo.upsertNotice(n)).isZero();
        assertThat(repo.findNotice("A1", "A1").orElseThrow().updatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("raw 가 바뀌면 2 — first_seen_at 은 남고 공고문 값(doc_*)은 비워 다시 받게 한다")
    void upsertChangedRawClearsDoc() {
        repo.upsertNotice(Fixtures.apt("A1").build());
        LocalDateTime first = repo.findNotice("A1", "A1").orElseThrow().firstSeenAt();
        repo.updateDoc("A1", "A1", NoticeDoc.builder().status("OK").rewinYears(10).regulation("투기과열").remndrHomeless(true).build());
        assertThat(repo.findNotice("A1", "A1").orElseThrow().docRewinYears()).isEqualTo(10);

        assertThat(repo.upsertNotice(Fixtures.apt("A1").rawJson("{\"changed\":1}").houseNm("바뀐 이름").build())).isEqualTo(2);
        Notice after = repo.findNotice("A1", "A1").orElseThrow();
        assertThat(after.firstSeenAt()).isEqualTo(first);
        assertThat(after.houseNm()).isEqualTo("바뀐 이름");
        assertThat(after.docStatus()).isNull();
        assertThat(after.docRewinYears()).isNull();
    }

    @Test
    @DisplayName("공고가 그대로면(0) 주택형을 다시 부르지 않는다")
    void saveWithTypesSkipsSupplierWhenUnchanged() {
        Notice n = Fixtures.apt("A1").build();
        AtomicInteger calls = new AtomicInteger();
        repo.saveNoticeWithTypes(n, () -> {
            calls.incrementAndGet();
            return List.of(Fixtures.type(n, "01", 84.97).build());
        });
        repo.saveNoticeWithTypes(n, () -> {
            calls.incrementAndGet();
            return List.of();
        });
        assertThat(calls).hasValue(1);
        assertThat(repo.findHouseTypes(List.of(n)).get(n.key())).hasSize(1);
    }

    @Test
    @DisplayName("주택형을 두 번 갈아 넣어도 겹치지 않는다")
    void replaceHouseTypesTwice() {
        Notice n = Fixtures.apt("A1").build();
        repo.upsertNotice(n);
        List<HouseType> types = List.of(Fixtures.type(n, "01", 59.98).build(), Fixtures.type(n, "02", 84.97).build());
        repo.replaceHouseTypes("A1", "A1", types);
        repo.replaceHouseTypes("A1", "A1", types);
        List<HouseType> got = repo.findHouseTypes(List.of(n)).get(n.key());
        assertThat(got).extracting(HouseType::modelNo).containsExactly("01", "02");
        assertThat(got.get(0).exclusiveArea()).isEqualByComparingTo("59.98");
    }

    @Test
    @DisplayName("지역 필터 — 서울·경기·인천을 주면 부산이 빠지고 공고일 최신순")
    void findNoticesByArea() {
        repo.upsertNotice(Fixtures.apt("S1").areaNm("서울").rcritPblancDe(LocalDate.of(2026, 10, 1)).build());
        repo.upsertNotice(Fixtures.apt("G1").areaNm("경기").rcritPblancDe(LocalDate.of(2026, 10, 5)).build());
        repo.upsertNotice(Fixtures.apt("B1").areaNm("부산").rcritPblancDe(LocalDate.of(2026, 10, 3)).build());
        List<Notice> got = repo.findNotices(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31), Set.of("서울", "경기", "인천"), Set.of(), 500);
        assertThat(got).extracting(Notice::houseManageNo).containsExactly("G1", "S1");
    }

    @Test
    @DisplayName("공고문 받을 대상 — 판정 대상이면서 공고문이 없는 것만, 공고일 최신순")
    void judgeTargetsWithoutDoc() {
        repo.upsertNotice(Fixtures.apt("APT_OLD").rcritPblancDe(LocalDate.of(2026, 9, 1)).build());
        repo.upsertNotice(Fixtures.apt("APT_NEW").rcritPblancDe(LocalDate.of(2026, 10, 9)).build());
        repo.upsertNotice(Fixtures.remndr("REM").rcritPblancDe(LocalDate.of(2026, 10, 5)).build());
        repo.upsertNotice(Fixtures.apt("HAS_DOC").build());
        repo.updateDoc("HAS_DOC", "HAS_DOC", NoticeDoc.failed("NO_LINK"));
        repo.upsertNotice(Fixtures.apt("OFFICE").kind("URBTY").houseSecd("02").build());
        repo.upsertNotice(Fixtures.apt("NEWLYWED").houseSecd("10").houseDtlSecd("03").build());
        repo.upsertNotice(Fixtures.apt("RENT").rentSecd("1").build());
        repo.upsertNotice(Fixtures.remndr("ILLEGAL").houseSecd("06").build());

        assertThat(repo.judgeTargetsWithoutDoc(10)).extracting(Notice::houseManageNo).containsExactly("APT_NEW", "REM", "APT_OLD");
        assertThat(repo.judgeTargetsWithoutDoc(1)).hasSize(1);
        assertThat(repo.countJudgeTargetsWithoutDoc()).isEqualTo(3);
    }

    @Test
    @DisplayName("수집 이력 — 넣고 닫고 최신순으로 꺼낸다, 마지막 성공의 since 를 돌려준다")
    void runs() {
        long a = repo.insertRun(CollectRun.builder().startedAt(NoticeRepository.now()).status(CollectRun.RUNNING)
                .triggerSrc("cron").sinceDate(LocalDate.of(2025, 10, 1)).build());
        repo.finishRun(a, CollectRun.builder().finishedAt(NoticeRepository.now()).status(CollectRun.OK).fetched(5).docPending(3).build());
        long b = repo.insertRun(CollectRun.builder().startedAt(NoticeRepository.now()).status(CollectRun.RUNNING)
                .triggerSrc("manual").sinceDate(LocalDate.of(2026, 9, 1)).build());

        assertThat(repo.recentRuns(3)).extracting(CollectRun::id).containsExactly(b, a);
        assertThat(repo.findRun(a).orElseThrow()).extracting(CollectRun::fetched, CollectRun::docPending).containsExactly(5, 3);
        assertThat(repo.lastSuccessfulSince()).contains(LocalDate.of(2025, 10, 1));
        assertThat(repo.hasRunning()).isTrue();
    }

    @Test
    @DisplayName("버려진 RUNNING — 기준 시각보다 먼저 시작한 것만 FAIL 로 닫는다")
    void failStaleRuns() {
        LocalDateTime now = NoticeRepository.now();
        long stale = repo.insertRun(CollectRun.builder().startedAt(now.minusMinutes(61)).status(CollectRun.RUNNING)
                .triggerSrc("cron").sinceDate(LocalDate.of(2026, 9, 1)).build());
        long fresh = repo.insertRun(CollectRun.builder().startedAt(now.minusMinutes(5)).status(CollectRun.RUNNING)
                .triggerSrc("cron").sinceDate(LocalDate.of(2026, 9, 1)).build());

        assertThat(repo.failStaleRuns(now.minusMinutes(60))).isEqualTo(1);
        assertThat(repo.findRun(stale).orElseThrow()).extracting(CollectRun::status, CollectRun::errorMessage)
                .containsExactly(CollectRun.FAIL, "중단됨");
        assertThat(repo.findRun(fresh).orElseThrow().status()).isEqualTo(CollectRun.RUNNING);
    }
}
