package com.portfolio.cheongyak;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 청약 공고·주택형·수집 이력 저장소. JdbcClient + SQL 이고, SQL 은 H2(PostgreSQL 모드)와 Postgres 양쪽에서 도는 것만 쓴다.
 * ON CONFLICT 를 H2 PG 모드가 거부해서 upsert 는 SELECT 뒤 INSERT·UPDATE 두 문장이다.
 * 행 → 레코드는 JdbcClient 의 query(Class) 가 컬럼 이름 스네이크 → 카멜로 맞춘다.
 */
@Repository
public class NoticeRepository {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final List<String> KEY_COLUMNS = List.of("house_manage_no", "pblanc_no");
    private static final List<String> API_COLUMNS = List.of(
            "kind", "house_secd", "house_secd_nm", "house_dtl_secd", "house_dtl_secd_nm", "rent_secd",
            "house_nm", "area_code", "area_nm", "address", "tot_suply_hshldco", "rcrit_pblanc_de",
            "rcept_bgnde", "rcept_endde", "spsply_bgnde", "spsply_endde",
            "rnk1_crsp_bgnde", "rnk1_crsp_endde", "rnk1_gg_bgnde", "rnk1_gg_endde", "rnk1_etc_bgnde", "rnk1_etc_endde",
            "rnk2_crsp_bgnde", "rnk2_crsp_endde", "rnk2_gg_bgnde", "rnk2_gg_endde", "rnk2_etc_bgnde", "rnk2_etc_endde",
            "przwner_de", "cntrct_bgnde", "cntrct_endde", "mvn_prearnge_ym",
            "speclt_rdn_at", "mdat_trget_at", "parcprc_uls_at", "imprmn_bsns_at", "public_house_at", "lrscl_bldlnd_at",
            "npln_pub_house_at", "bsns_mby_nm", "cnstrct_entrps_nm", "hmpg_adres", "pblanc_url");
    private static final List<String> DOC_COLUMNS = List.of(
            "doc_status", "doc_remndr_area", "doc_remndr_homeless", "doc_rewin_years", "doc_regulation",
            "doc_crsp_city", "doc_crsp_months", "doc_res_pri_area", "doc_res_pri_months",
            "doc_point_le60", "doc_point_le85", "doc_point_gt85", "doc_fetched_at");
    private static final List<String> HOUSE_TYPE_COLUMNS = List.of(
            "house_manage_no", "pblanc_no", "model_no", "house_ty", "exclusive_area", "supply_area",
            "suply_hshldco", "spsply_hshldco", "mnych_hshldco", "nwwds_hshldco", "lfe_frst_hshldco",
            "old_parnts_hshldco", "instt_hshldco", "etc_hshldco", "transr_hshldco", "ygmn_hshldco", "nwbb_hshldco",
            "top_amount", "raw_json");

    private static final String INSERT_NOTICE = insert("cheongyak_notice",
            concat(KEY_COLUMNS, API_COLUMNS, DOC_COLUMNS, List.of("raw_json", "first_seen_at", "updated_at")));
    // raw 가 바뀐 공고는 공고문도 다시 받는다 — doc_* 를 비워 다음 공고문 단계가 집게 한다
    private static final String UPDATE_NOTICE = "UPDATE cheongyak_notice SET "
            + API_COLUMNS.stream().map(c -> c + " = :" + camel(c)).collect(Collectors.joining(", "))
            + ", " + DOC_COLUMNS.stream().map(c -> c + " = NULL").collect(Collectors.joining(", "))
            + ", raw_json = :rawJson, updated_at = :updatedAt"
            + " WHERE house_manage_no = :houseManageNo AND pblanc_no = :pblancNo";
    private static final String INSERT_HOUSE_TYPE = insert("cheongyak_house_type", HOUSE_TYPE_COLUMNS);
    // 판정 대상: APT 민영·국민(분양전환 임대 제외) + 무순위(04). 공고문은 이것만 받는다
    private static final String JUDGE_TARGET = "((kind = 'APT' AND house_secd = '01' AND house_dtl_secd IN ('01', '03')"
            + " AND COALESCE(rent_secd, '0') <> '1') OR (kind = 'REMNDR' AND house_secd = '04'))";

    private final JdbcClient jdbc;

    public NoticeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 없으면 INSERT(1), raw 가 다르면 UPDATE(2, first_seen_at 보존·doc_* 비움), 같으면 아무것도 안 함(0) */
    public int upsertNotice(Notice n) {
        Optional<String> raw = jdbc.sql("SELECT raw_json FROM cheongyak_notice WHERE house_manage_no = :h AND pblanc_no = :p")
                .param("h", n.houseManageNo()).param("p", n.pblancNo())
                .query(String.class).optional();
        LocalDateTime now = now();
        if (raw.isEmpty()) {
            jdbc.sql(INSERT_NOTICE).paramSource(params(n).addValue("firstSeenAt", now).addValue("updatedAt", now)).update();
            return 1;
        }
        if (raw.get().equals(n.rawJson())) {
            return 0;
        }
        jdbc.sql(UPDATE_NOTICE).paramSource(params(n).addValue("updatedAt", now)).update();
        return 2;
    }

    public void replaceHouseTypes(String hmn, String pno, List<HouseType> types) {
        jdbc.sql("DELETE FROM cheongyak_house_type WHERE house_manage_no = :h AND pblanc_no = :p")
                .param("h", hmn).param("p", pno).update();
        for (HouseType t : types) {
            jdbc.sql(INSERT_HOUSE_TYPE).paramSource(params(t)).update();
        }
    }

    /**
     * 공고 한 건과 주택형을 한 트랜잭션으로 저장한다. 공고가 새로 들어왔거나 바뀌었을 때만 주택형을 다시 부른다.
     * 수집 전체를 한 트랜잭션으로 묶지 않으려고 여기서 경계를 끊는다(다른 빈이라 프록시가 먹는다).
     */
    @Transactional
    public int saveNoticeWithTypes(Notice n, Supplier<List<HouseType>> typesIfChanged) {
        int result = upsertNotice(n);
        if (result != 0) {
            replaceHouseTypes(n.houseManageNo(), n.pblancNo(), typesIfChanged.get());
        }
        return result;
    }

    /** 공고일 내림차순. areaNames·kinds 가 비면 거르지 않는다 */
    public List<Notice> findNotices(LocalDate from, LocalDate to, Set<String> areaNames, Set<String> kinds, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM cheongyak_notice WHERE rcrit_pblanc_de BETWEEN :from AND :to");
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("from", from).addValue("to", to).addValue("limit", limit);
        if (!areaNames.isEmpty()) {
            sql.append(" AND area_nm IN (:areas)");
            p.addValue("areas", areaNames);
        }
        if (!kinds.isEmpty()) {
            sql.append(" AND kind IN (:kinds)");
            p.addValue("kinds", kinds);
        }
        sql.append(" ORDER BY rcrit_pblanc_de DESC, house_manage_no DESC LIMIT :limit");
        return jdbc.sql(sql.toString()).paramSource(p).query(Notice.class).list();
    }

    public Optional<Notice> findNotice(String hmn, String pno) {
        return jdbc.sql("SELECT * FROM cheongyak_notice WHERE house_manage_no = :h AND pblanc_no = :p")
                .param("h", hmn).param("p", pno).query(Notice.class).optional();
    }

    /** key = 공고 key(). 주택형은 model_no 순 */
    public Map<String, List<HouseType>> findHouseTypes(List<Notice> notices) {
        Map<String, List<HouseType>> out = new LinkedHashMap<>();
        if (notices.isEmpty()) {
            return out;
        }
        Set<String> keys = notices.stream().map(Notice::key).collect(Collectors.toSet());
        List<String> hmns = notices.stream().map(Notice::houseManageNo).distinct().toList();
        List<HouseType> rows = jdbc.sql("SELECT * FROM cheongyak_house_type WHERE house_manage_no IN (:hmns) ORDER BY house_manage_no, pblanc_no, model_no")
                .param("hmns", hmns).query(HouseType.class).list();
        for (HouseType t : rows) {
            if (keys.contains(t.noticeKey())) {
                out.computeIfAbsent(t.noticeKey(), k -> new ArrayList<>()).add(t);
            }
        }
        return out;
    }

    /** 공고문을 아직 안 받은 판정 대상. 공고일 최신순 */
    public List<Notice> judgeTargetsWithoutDoc(int limit) {
        return jdbc.sql("SELECT * FROM cheongyak_notice WHERE doc_status IS NULL AND " + JUDGE_TARGET
                        + " ORDER BY rcrit_pblanc_de DESC, house_manage_no DESC LIMIT :limit")
                .param("limit", limit).query(Notice.class).list();
    }

    public int countJudgeTargetsWithoutDoc() {
        return jdbc.sql("SELECT COUNT(*) FROM cheongyak_notice WHERE doc_status IS NULL AND " + JUDGE_TARGET)
                .query(Integer.class).single();
    }

    public void updateDoc(String hmn, String pno, NoticeDoc d) {
        jdbc.sql("UPDATE cheongyak_notice SET doc_status = :status, doc_remndr_area = :remndrArea,"
                        + " doc_remndr_homeless = :homeless, doc_rewin_years = :rewinYears, doc_regulation = :regulation,"
                        + " doc_crsp_city = :crspCity, doc_crsp_months = :crspMonths, doc_res_pri_area = :resPriArea,"
                        + " doc_res_pri_months = :resPriMonths, doc_point_le60 = :pointLe60, doc_point_le85 = :pointLe85,"
                        + " doc_point_gt85 = :pointGt85, doc_fetched_at = :now"
                        + " WHERE house_manage_no = :h AND pblanc_no = :p")
                .paramSource(params(d)
                        .addValue("homeless", d.remndrHomeless() == null ? null : (d.remndrHomeless() ? "Y" : "N"))
                        .addValue("now", now()).addValue("h", hmn).addValue("p", pno))
                .update();
    }

    public long insertRun(CollectRun r) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO cheongyak_collect_run (started_at, status, trigger_src, since_date)"
                        + " VALUES (:startedAt, :status, :triggerSrc, :sinceDate)")
                .paramSource(params(r)).update(key, "id");
        return key.getKey().longValue();
    }

    public void finishRun(long id, CollectRun r) {
        jdbc.sql("UPDATE cheongyak_collect_run SET finished_at = :finishedAt, status = :status, fetched = :fetched,"
                        + " inserted = :inserted, updated = :updated, remndr_count = :remndrCount, api_calls = :apiCalls,"
                        + " doc_fetched = :docFetched, doc_ok = :docOk, doc_pending = :docPending, error_message = :errorMessage"
                        + " WHERE id = :runId")
                .paramSource(params(r).addValue("runId", id)).update();
    }

    public Optional<CollectRun> findRun(long id) {
        return jdbc.sql("SELECT * FROM cheongyak_collect_run WHERE id = :id").param("id", id).query(CollectRun.class).optional();
    }

    public boolean hasRunning() {
        return jdbc.sql("SELECT COUNT(*) FROM cheongyak_collect_run WHERE status = 'RUNNING'").query(Integer.class).single() > 0;
    }

    /** 재기동 등으로 버려진 RUNNING 을 닫는다. 닫은 수를 돌려준다 */
    public int failStaleRuns(LocalDateTime startedBefore) {
        return jdbc.sql("UPDATE cheongyak_collect_run SET status = 'FAIL', error_message = '중단됨', finished_at = :now"
                        + " WHERE status = 'RUNNING' AND started_at < :before")
                .param("now", now()).param("before", startedBefore).update();
    }

    public List<CollectRun> recentRuns(int limit) {
        return jdbc.sql("SELECT * FROM cheongyak_collect_run ORDER BY id DESC LIMIT :limit")
                .param("limit", limit).query(CollectRun.class).list();
    }

    public Optional<LocalDate> lastSuccessfulSince() {
        return jdbc.sql("SELECT since_date FROM cheongyak_collect_run WHERE status = 'OK' ORDER BY id DESC LIMIT 1")
                .query(LocalDate.class).optional();
    }

    /** 저장 시각은 한국 시간으로 남긴다. 화면이 「N시간 전」 을 한국 시간 기준으로 그린다 */
    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    private static MapSqlParameterSource params(Record r) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (RecordComponent c : r.getClass().getRecordComponents()) {
            try {
                p.addValue(c.getName(), c.getAccessor().invoke(r));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(c.getName(), e);
            }
        }
        return p;
    }

    private static String insert(String table, List<String> columns) {
        return "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                + columns.stream().map(c -> ":" + camel(c)).collect(Collectors.joining(", ")) + ")";
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        return Stream.of(lists).flatMap(List::stream).toList();
    }

    private static String camel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : snake.toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }
}
