package com.portfolio.cheongyak.dto;

import com.portfolio.cheongyak.CmpetRow;
import com.portfolio.cheongyak.HouseType;
import com.portfolio.cheongyak.Notice;
import com.portfolio.cheongyak.ScoreRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 화면이 쓰는 공고 한 건. 원문(raw_json)은 빼고, 공고문 추출값은 doc 으로 묶는다.
 * 경쟁률·당첨가점(cmpet·scores)은 발표가 끝난 공고에만 있고, 발표 전 APT 민영에는 같은 지역 당첨가점 참고치(refScore)를 붙인다.
 * 주택형의 mkt* 는 실거래 시세(만원) — 배지(저렴·수준·비쌈)는 화면이 최고 분양가와 나눠 정한다.
 */
public record NoticeView(
        String houseManageNo,
        String pblancNo,
        String kind,
        String kindLabel,
        String houseSecd,
        String houseDtlSecd,
        String houseNm,
        String areaNm,
        String address,
        Integer totSuplyHshldco,
        LocalDate rcritPblancDe,
        LocalDate rceptBgnde,
        LocalDate rceptEndde,
        LocalDate spsplyBgnde,
        LocalDate spsplyEndde,
        LocalDate rnk1CrspBgnde,
        LocalDate rnk1CrspEndde,
        LocalDate rnk1GgBgnde,
        LocalDate rnk1GgEndde,
        LocalDate rnk1EtcBgnde,
        LocalDate rnk1EtcEndde,
        LocalDate przwnerDe,
        String specltRdnAt,
        String mdatTrgetAt,
        boolean regulated,
        boolean pubHousing,
        String pblancUrl,
        String hmpgAdres,
        Doc doc,
        List<Type> houseTypes,
        LocalDateTime firstSeenAt,
        String resultStatus,
        List<Cmpet> cmpet,
        List<Score> scores,
        BigDecimal topRate,
        Integer minScore,
        RefScore refScore,
        GeoView geo
) {

    /** 공고문에서 뽑은 값. status 가 null 이면 아직 안 받았다 */
    public record Doc(String status, String remndrArea, Boolean remndrHomeless, Integer rewinYears, String regulation,
                      String crspCity, Integer crspMonths, String resPriArea, Integer resPriMonths,
                      Integer pointLe60, Integer pointLe85, Integer pointGt85) {
    }

    public record Type(String modelNo, String houseTy, BigDecimal exclusiveArea, BigDecimal supplyArea,
                       Integer suplyHshldco, Integer spsplyHshldco, Long topAmount,
                       Long mktMedian, Integer mktCount, String mktBasis) {
    }

    /** 경쟁률 한 줄. cmpetRate 가 null 이면 cmpetText 가 원래 표기(미달 등) */
    public record Cmpet(String houseTy, String resideNm, int rankCode, Integer suplyHshldco, Integer reqCnt,
                        BigDecimal cmpetRate, String cmpetText) {
    }

    public record Score(String houseTy, String resideNm, Integer minScore, Integer maxScore, BigDecimal avgScore) {
    }

    /** 같은 시군구(LAWD) 또는 같은 시도(AREA) 최근 1년 APT 민영 해당지역 최저 당첨가점의 중앙값과 주택형 수 */
    public record RefScore(Integer median, int count, String basis) {
    }

    public record GeoView(String lawdCd, String umdNm, BigDecimal lat, BigDecimal lng) {
    }

    public static NoticeView of(Notice n, List<HouseType> types) {
        return of(n, types, List.of(), List.of(), null);
    }

    public static NoticeView of(Notice n, List<HouseType> types, List<CmpetRow> cmpet, List<ScoreRow> scores, RefScore ref) {
        Doc doc = new Doc(n.docStatus(), n.docRemndrArea(),
                n.docRemndrHomeless() == null ? null : "Y".equals(n.docRemndrHomeless()),
                n.docRewinYears(), n.docRegulation(), n.docCrspCity(), n.docCrspMonths(), n.docResPriArea(),
                n.docResPriMonths(), n.docPointLe60(), n.docPointLe85(), n.docPointGt85());
        List<Type> ts = types.stream().map(t -> new Type(t.modelNo(), t.houseTy(), t.exclusiveArea(), t.supplyArea(),
                t.suplyHshldco(), t.spsplyHshldco(), t.topAmount(), t.mktMedian(), t.mktCount(), t.mktBasis())).toList();
        List<Cmpet> cs = cmpet.stream().map(c -> new Cmpet(c.houseTy(), c.resideNm(), c.rankCode(), c.suplyHshldco(),
                c.reqCnt(), c.cmpetRate(), c.cmpetText())).toList();
        List<Score> ss = scores.stream().map(c -> new Score(c.houseTy(), c.resideNm(), c.minScore(), c.maxScore(), c.avgScore())).toList();
        GeoView geo = n.lawdCd() == null ? null : new GeoView(n.lawdCd(), n.umdNm(), n.lat(), n.lng());
        return new NoticeView(n.houseManageNo(), n.pblancNo(), n.kind(), kindLabel(n), n.houseSecd(), n.houseDtlSecd(),
                n.houseNm(), n.areaNm(), n.address(), n.totSuplyHshldco(), n.rcritPblancDe(), n.rceptBgnde(), n.rceptEndde(),
                n.spsplyBgnde(), n.spsplyEndde(), n.rnk1CrspBgnde(), n.rnk1CrspEndde(), n.rnk1GgBgnde(), n.rnk1GgEndde(),
                n.rnk1EtcBgnde(), n.rnk1EtcEndde(), n.przwnerDe(), n.specltRdnAt(), n.mdatTrgetAt(),
                "Y".equals(n.specltRdnAt()) || "Y".equals(n.mdatTrgetAt()),
                "Y".equals(n.publicHouseAt()) || "Y".equals(n.nplnPubHouseAt()),
                n.pblancUrl(), n.hmpgAdres(), doc, ts, n.firstSeenAt(), n.resultStatus(), cs, ss,
                topRate(cmpet), minScore(scores), ref, geo);
    }

    /** 1순위 해당지역 최고 경쟁률. 무순위는 순위가 없어 전체 최고 */
    static BigDecimal topRate(List<CmpetRow> rows) {
        return rows.stream()
                .filter(r -> r.rankCode() == 0 || r.rankCode() == 1 && r.resideNm() != null && r.resideNm().startsWith("해당"))
                .map(CmpetRow::cmpetRate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    /** 해당지역 주택형 가운데 가장 낮은 최저 당첨가점 */
    static Integer minScore(List<ScoreRow> rows) {
        return rows.stream().filter(r -> r.resideNm() != null && r.resideNm().startsWith("해당"))
                .map(ScoreRow::minScore).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    static String kindLabel(Notice n) {
        String secd = n.houseSecd() == null ? "" : n.houseSecd();
        return switch (n.kind()) {
            case "APT" -> switch (secd) {
                case "09" -> "민간사전청약";
                case "10" -> "신혼희망타운";
                default -> "1".equals(n.rentSecd()) ? "분양전환 임대" : "03".equals(n.houseDtlSecd()) ? "APT 국민" : "APT 민영";
            };
            case "REMNDR" -> "06".equals(secd) ? "불법행위 재공급" : "무순위";
            case "URBTY" -> n.houseDtlSecdNm() != null ? n.houseDtlSecdNm() : "오피스텔·도시형";
            case "PBLPVT" -> "공공지원 민간임대";
            case "OPT" -> "임의공급";
            default -> n.kind();
        };
    }
}
