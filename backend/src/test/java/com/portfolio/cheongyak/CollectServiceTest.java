package com.portfolio.cheongyak;

import com.portfolio.error.UpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 저장소는 H2 실물, 청약홈 API·공고문은 가짜. 작업 스레드는 같은 스레드로 바꿔 끝까지 기다린다
@SpringBootTest
@ActiveProfiles("test")
class CollectServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate SINCE = LocalDate.of(2025, 10, 25);

    @Autowired
    private CollectService service;

    @Autowired
    private NoticeRepository repo;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private ApplyhomeClient client;

    @MockitoBean
    private NoticeDocClient docClient;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM cheongyak_house_type").update();
        jdbc.sql("DELETE FROM cheongyak_notice").update();
        jdbc.sql("DELETE FROM cheongyak_collect_run").update();
        ReflectionTestUtils.setField(service, "worker", (Executor) Runnable::run);
        ReflectionTestUtils.setField(service, "docBatch", 150);
        when(client.fetchHouseTypes(any(), anyString())).thenReturn(List.of(node(
                "{\"MODEL_NO\":\"01\",\"HOUSE_TY\":\"084.9700A\",\"SUPLY_HSHLDCO\":10,\"SPSPLY_HSHLDCO\":5,\"LTTOT_TOP_AMOUNT\":\"55,000\"}")));
        when(docClient.fetch(anyString(), anyString()))
                .thenReturn(NoticeDoc.builder().status("OK").remndrArea("경기").remndrHomeless(true).rewinYears(0).build());
    }

    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }

    private static JsonNode detail(String hmn, String houseSecd, String dtl, String name) {
        return node("{\"HOUSE_MANAGE_NO\":\"" + hmn + "\",\"PBLANC_NO\":\"" + hmn + "\",\"HOUSE_SECD\":\"" + houseSecd + "\","
                + (dtl == null ? "" : "\"HOUSE_DTL_SECD\":\"" + dtl + "\",")
                + "\"HOUSE_NM\":\"" + name + "\",\"SUBSCRPT_AREA_CODE_NM\":\"경기\",\"RCRIT_PBLANC_DE\":\"2026-10-01\","
                + "\"PBLANC_URL\":\"https://example.invalid/" + hmn + "\"}");
    }

    /** APT 새 공고, 무순위 raw 바뀐 공고, 오피스텔 새 공고, 공공지원 raw 같은 공고, 임의공급 새 공고 */
    private void fiveKinds() {
        JsonNode apt = detail("1", "01", "01", "민영 새 공고");
        JsonNode remndr = detail("2", "04", null, "무순위 바뀐 공고");
        JsonNode office = detail("3", "02", "02", "오피스텔");
        JsonNode pblpvt = detail("4", "03", null, "공공지원 그대로");
        JsonNode opt = detail("5", "05", null, "임의공급");
        repo.upsertNotice(NoticeMapper.toNotice(ApplyhomeKind.REMNDR, detail("2", "04", null, "무순위 옛 이름")));
        repo.upsertNotice(NoticeMapper.toNotice(ApplyhomeKind.PBLPVT, pblpvt));
        when(client.fetchDetails(eq(ApplyhomeKind.APT), any())).thenReturn(List.of(apt));
        when(client.fetchDetails(eq(ApplyhomeKind.REMNDR), any())).thenReturn(List.of(remndr));
        when(client.fetchDetails(eq(ApplyhomeKind.URBTY), any())).thenReturn(List.of(office));
        when(client.fetchDetails(eq(ApplyhomeKind.PBLPVT), any())).thenReturn(List.of(pblpvt));
        when(client.fetchDetails(eq(ApplyhomeKind.OPT), any())).thenReturn(List.of(opt));
    }

    private long running() {
        return repo.insertRun(CollectRun.builder().startedAt(NoticeRepository.now()).status(CollectRun.RUNNING)
                .triggerSrc("manual").sinceDate(SINCE).build());
    }

    @Test
    @DisplayName("다섯 종류 — 받음 5·신규 3·갱신 1·무순위 1, 주택형은 바뀐 4건만 부르고 공고문은 판정 대상 2건만 받는다")
    void collectsFiveKinds() {
        fiveKinds();
        CollectRun run = service.run(running(), SINCE);

        assertThat(run.status()).isEqualTo(CollectRun.OK);
        assertThat(run).extracting(CollectRun::fetched, CollectRun::inserted, CollectRun::updated, CollectRun::remndrCount)
                .containsExactly(5, 3, 1, 1);
        verify(client, times(4)).fetchHouseTypes(any(), anyString());
        verify(docClient, times(2)).fetch(anyString(), anyString());
        assertThat(run).extracting(CollectRun::docFetched, CollectRun::docOk, CollectRun::docPending).containsExactly(2, 2, 0);

        Notice apt = repo.findNotice("1", "1").orElseThrow();
        assertThat(apt.docStatus()).isEqualTo("OK");
        assertThat(repo.findHouseTypes(List.of(apt)).get(apt.key()).get(0).topAmount()).isEqualTo(55000L);
        assertThat(repo.findNotice("2", "2").orElseThrow().houseNm()).isEqualTo("무순위 바뀐 공고");
    }

    @Test
    @DisplayName("공고문을 못 받아도(NO_LINK) 수집은 OK, 받은 수 0")
    void docFailureKeepsRunOk() {
        fiveKinds();
        when(docClient.fetch(anyString(), anyString())).thenReturn(NoticeDoc.failed("NO_LINK"));
        CollectRun run = service.run(running(), SINCE);
        assertThat(run.status()).isEqualTo(CollectRun.OK);
        assertThat(run).extracting(CollectRun::docFetched, CollectRun::docOk, CollectRun::docPending).containsExactly(2, 0, 0);
        assertThat(repo.findNotice("1", "1").orElseThrow().docStatus()).isEqualTo("NO_LINK");
    }

    @Test
    @DisplayName("추출값이 컬럼에 안 들어가는 공고는 SAVE_ERR 로 표시하고 다음 공고문을 계속 받는다")
    void docSaveErrorDoesNotStopOthers() {
        fiveKinds();
        when(docClient.fetch(eq("REMNDR"), anyString()))
                .thenReturn(NoticeDoc.builder().status("OK").remndrArea("해당 주택건설지역인 수원특례시").build());
        CollectRun run = service.run(running(), SINCE);
        assertThat(run).extracting(CollectRun::docFetched, CollectRun::docOk, CollectRun::docPending).containsExactly(2, 1, 0);
        assertThat(repo.findNotice("2", "2").orElseThrow().docStatus()).isEqualTo(NoticeDoc.SAVE_ERR);
        assertThat(repo.findNotice("1", "1").orElseThrow().docStatus()).isEqualTo("OK");
    }

    @Test
    @DisplayName("세 번째 종류에서 API 가 실패하면 FAIL 로 닫고 앞 두 건은 남는다")
    void apiFailureClosesRunButKeepsSaved() {
        fiveKinds();
        when(client.fetchDetails(eq(ApplyhomeKind.URBTY), any()))
                .thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "청약홈 API 응답이 정상이 아니다"));
        CollectRun run = service.run(running(), SINCE);

        assertThat(run.status()).isEqualTo(CollectRun.FAIL);
        assertThat(run.errorMessage()).startsWith("UPSTREAM_ERROR");
        assertThat(run.fetched()).isEqualTo(2);
        assertThat(repo.findNotice("1", "1")).isPresent();
        verify(docClient, never()).fetch(anyString(), anyString());
    }

    @Test
    @DisplayName("공고문은 실행당 batch-size 만큼 — 남은 수를 doc_pending 에 남긴다")
    void docBatchLimit() {
        fiveKinds();
        ReflectionTestUtils.setField(service, "docBatch", 1);
        CollectRun run = service.run(running(), SINCE);
        assertThat(run).extracting(CollectRun::docFetched, CollectRun::docPending).containsExactly(1, 1);
    }

    @Test
    @DisplayName("RUNNING 이 있으면 시작하지 않는다")
    void busy() {
        running();
        assertThatThrownBy(() -> service.start("cron")).isInstanceOf(CollectBusyException.class);
    }

    @Test
    @DisplayName("61분 전 RUNNING 은 버려진 것으로 닫고 새로 시작한다. 첫 실행은 365일 창")
    void staleRunIsClosed() {
        long stale = repo.insertRun(CollectRun.builder().startedAt(NoticeRepository.now().minusMinutes(61))
                .status(CollectRun.RUNNING).triggerSrc("cron").sinceDate(SINCE).build());
        CollectRun started = service.start("cron");

        assertThat(repo.findRun(stale).orElseThrow().status()).isEqualTo(CollectRun.FAIL);
        assertThat(started.sinceDate()).isEqualTo(NoticeRepository.now().toLocalDate().minusDays(365));
        assertThat(repo.findRun(started.id()).orElseThrow().status()).isEqualTo(CollectRun.OK);
    }
}
